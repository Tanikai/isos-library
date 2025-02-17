package bftsmart.communication;

public interface MessageHandler {
    void processData(SystemMessage sm);

    void verifyPending();
}
