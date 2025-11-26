package com.example.circularslider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FrameProcessor {

    // --- Constantes du protocole (Section 2.3) ---
    private static final byte HEADER = (byte) 0x05;
    private static final byte TAIL = (byte) 0x04;
    private static final byte ESCAPE = (byte) 0x06;

    // --- Machine à états du décodage ---
    private enum State {
        SEARCH_HEADER, // Recherche de l’en-tête (0x05)
        GET_LENGTH_H,  // Lecture de l’octet de poids fort de LENGTH
        GET_LENGTH_L,  // Lecture de l’octet de poids faible de LENGTH
        GET_PAYLOAD,   // Lecture du PAYLOAD + CTRL
        GET_TAIL       // Lecture de l’octet de fin (0x04)
    }

    // --- Variables d’état du décodeur ---
    private State currentState = State.SEARCH_HEADER;
    private boolean waitingForEscape = false; // Indique si un octet d’échappement doit être traité
    private int payloadLength = 0;            // N (taille effective du payload, sans échappement)
    private int bytesRead = 0;                // Nombre total de bytes lus (PAYLOAD + CTRL)
    private int totalPayloadAndCtrlSize = 0;  // N + 1 (PAYLOAD + CTRL)
    private byte[] rawPayloadAndCtrl = null;  // Stockage du PAYLOAD + CTRL (après déséchapement)
    private Data decodedData = null;          // Dernière trame décodée
    private boolean frameAvailable = false;   // Indique qu’une trame complète est prête à être lue

    /**
     * Classe interne Data : contient les informations extraites d’une trame complète.
     * Elle regroupe l'identifiant de commande, les paramètres et la validité de la trame.
     */
    public class Data {
        private final byte commandId;
        private final byte[] parameters;
        private final boolean frameValidity;

        public Data(byte id, byte[] params, boolean validity) {
            this.commandId = id;
            this.parameters = params;
            this.frameValidity = validity;
        }

        public byte getId() {
            return commandId; // Identifiant de commande
        }

        public byte[] getParam() {
            // Retourne une copie pour éviter toute modification externe
            return parameters != null ? parameters.clone() : new byte[0];
        }

        public boolean getFrameValidity() {
            return frameValidity; // Validité de la trame (checksum correct ou non)
        }
    }

    // ----------------------------------------------------------------------
    // --- 1. Méthode d’encodage : public byte[] toFrame(byte[] c) ---
    // ----------------------------------------------------------------------

    /**
     * Encode un payload (c) en une trame complète conforme au protocole.
     * @param c Payload (ID de commande + paramètres)
     * @return Tableau d’octets représentant la trame encodée.
     */
    public byte[] toFrame(byte[] c) {
        int payloadLen = c.length; // N

        // 1. Calcul de LENGTH (2 octets)
        byte lengthH = (byte) ((payloadLen >> 8) & 0xFF);
        byte lengthL = (byte) (payloadLen & 0xFF);

        // 2. Calcul du checksum (CTRL) – complément modulo 256
        int sum = (lengthH & 0xFF) + (lengthL & 0xFF);
        for (byte b : c) {
            sum += (b & 0xFF);
        }
        // CTRL = 256 - (Sum mod 256)
        byte ctrl = (byte) (0x100 - (sum % 0x100));

        // 3. Application du byte-stuffing (échappement) après calcul de LENGTH + CTRL
        List<Byte> stuffedData = new ArrayList<>();

        // Fonction utilitaire : ajoute un octet avec échappement si nécessaire
        Consumer<Byte> addByteWithStuffing = (b) -> {
            // Valeurs nécessitant un échappement : 0x04 (TAIL), 0x05 (HEADER), 0x06 (ESCAPE)
            if (b == TAIL || b == HEADER || b == ESCAPE) {
                stuffedData.add(ESCAPE); // Ajout du marqueur d’échappement
                stuffedData.add((byte) (b + ESCAPE)); // Ajout de la valeur échappée
            } else {
                stuffedData.add(b);
            }
        };

        // a. LENGTH
        addByteWithStuffing.accept(lengthH);
        addByteWithStuffing.accept(lengthL);

        // b. PAYLOAD
        for (byte b : c) {
            addByteWithStuffing.accept(b);
        }

        // c. CTRL
        addByteWithStuffing.accept(ctrl);

        // 4. Construction finale de la trame
        int frameSize = 2 + stuffedData.size(); // HEADER + TAIL + données échappées
        byte[] frame = new byte[frameSize];
        frame[0] = HEADER;

        int i = 1;
        for (byte b : stuffedData) {
            frame[i++] = b;
        }
        frame[frameSize - 1] = TAIL;

        return frame;
    }

    // ----------------------------------------------------------------------
    // --- 2. Méthode de décodage : public void fromFrame(byte data) ---
    // ----------------------------------------------------------------------

    /**
     * Intègre un octet reçu dans le processus de décodage.
     * @param data Octet brut reçu du flux entrant.
     */
    public void fromFrame(byte data) {
        if (frameAvailable) {
            // Si une trame précédente n’a pas encore été lue, on ignore l’octet courant
            return;
        }

        // --- 1. Gestion du déséchappement (un-stuffing) ---
        byte workingData = data;

        if (waitingForEscape) {
            // L’octet précédent était ESCAPE → l’octet actuel est une valeur échappée
            workingData = (byte) (workingData - ESCAPE);
            waitingForEscape = false;
        } else if (data == ESCAPE) {
            // Octet ESCAPE → le suivant devra être déséchapé
            waitingForEscape = true;
            return;
        }

        // --- 2. Machine à états du décodage ---
        switch (currentState) {
            case SEARCH_HEADER:
                if (workingData == HEADER) {
                    currentState = State.GET_LENGTH_H;
                    payloadLength = 0;
                    bytesRead = 0;
                    rawPayloadAndCtrl = null;
                }
                break;

            case GET_LENGTH_H:
                payloadLength = (workingData & 0xFF) << 8;
                currentState = State.GET_LENGTH_L;
                break;

            case GET_LENGTH_L:
                payloadLength |= (workingData & 0xFF);
                totalPayloadAndCtrlSize = payloadLength + 1; // PAYLOAD (N) + CTRL (1)

                if (payloadLength <= 0 || totalPayloadAndCtrlSize > 4096) {
                    // Longueur invalide ou trop grande (4K comme limite de sécurité)
                    currentState = State.SEARCH_HEADER;
                } else {
                    rawPayloadAndCtrl = new byte[totalPayloadAndCtrlSize];
                    currentState = State.GET_PAYLOAD;
                }
                break;

            case GET_PAYLOAD:
                if (bytesRead < totalPayloadAndCtrlSize) {
                    rawPayloadAndCtrl[bytesRead++] = workingData;
                }
                if (bytesRead == totalPayloadAndCtrlSize) {
                    currentState = State.GET_TAIL;
                }
                break;

            case GET_TAIL:
                if (workingData == TAIL) {
                    // Fin de trame détectée, vérification + extraction des données
                    boolean validity = checkFrameIntegrity();

                    byte commandId = rawPayloadAndCtrl[0];
                    int paramLength = payloadLength - 1;
                    byte[] parameters = new byte[paramLength];
                    if (paramLength > 0) {
                        System.arraycopy(rawPayloadAndCtrl, 1, parameters, 0, paramLength);
                    }

                    decodedData = new Data(commandId, parameters, validity);
                    frameAvailable = true; // Trame complète prête à être lue
                }

                // Dans tous les cas, retour à la recherche d’un nouvel HEADER
                currentState = State.SEARCH_HEADER;
                break;

            default:
                currentState = State.SEARCH_HEADER;
        }
    }

    /**
     * Méthode interne : vérifie l’intégrité de la trame via le checksum (CTRL).
     * @return true si le checksum est correct, false sinon.
     */
    private boolean checkFrameIntegrity() {
        if (rawPayloadAndCtrl == null || totalPayloadAndCtrlSize <= 1) {
            return false; // Données insuffisantes
        }

        // 1. CTRL reçu (dernier octet)
        byte receivedCtrl = rawPayloadAndCtrl[totalPayloadAndCtrlSize - 1];

        // 2. Reconstruction de LENGTH
        byte lengthH = (byte) ((payloadLength >> 8) & 0xFF);
        byte lengthL = (byte) (payloadLength & 0xFF);

        // 3. Calcul de la somme (LENGTH + PAYLOAD)
        int sum = (lengthH & 0xFF) + (lengthL & 0xFF);

        int payloadOnlyLength = totalPayloadAndCtrlSize - 1;
        for (int i = 0; i < payloadOnlyLength; i++) {
            sum += (rawPayloadAndCtrl[i] & 0xFF);
        }

        // 4. CTRL attendu
        byte expectedCtrl = (byte) (0x100 - (sum % 0x100));

        // 5. Comparaison
        return expectedCtrl == receivedCtrl;
    }

    // ----------------------------------------------------------------------
    // --- 3. Indicateur d’état : public boolean framePending() ---
    // ----------------------------------------------------------------------

    /**
     * Retourne true lorsqu’une trame complète a été décodée et attend d’être lue.
     */
    public boolean framePending() {
        return frameAvailable;
    }

    // ----------------------------------------------------------------------
    // --- 4. Récupération des données : public Data getData() ---
    // ----------------------------------------------------------------------

    /**
     * Retourne les données de la dernière trame reçue,
     * puis réinitialise l’indicateur permettant le décodage d’une nouvelle trame.
     * @return Objet Data contenant la trame décodée, ou null si aucune trame n’est disponible.
     */
    public Data getData() {
        if (frameAvailable) {
            frameAvailable = false; // Réinitialise l'état pour accepter une nouvelle trame
            return decodedData;
        }
        return null;
    }
}
