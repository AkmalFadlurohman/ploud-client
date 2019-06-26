package ploud.rentor.util;

import ploud.rentor.model.RentorFile;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;

public interface RentorTask {
    public abstract void prepareFileReceive(String renterFileData);
    public abstract void receiveFile(DataInputStream streamIn);
    public abstract void completeFileReceive();
    public abstract void prepareRequestedFile(String renterFileData);
    public abstract boolean sendRequestedFile(DataOutputStream streamOut);
    public abstract void reloadWallet();
    public abstract void deleteFile(File renterFile);
}
