package buildcraftreforged.core.network;

/**
 * Thrown while reading a client-to-server packet whose content is out of range or does not fit the open menu. The
 * packet handler logs the reason and drops the packet; the sender stays connected.
 */
public class InvalidPacketException extends Exception {
    public InvalidPacketException(String message) {
        super(message);
    }
}
