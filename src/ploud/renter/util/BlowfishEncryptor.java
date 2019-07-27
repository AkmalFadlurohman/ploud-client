package ploud.renter.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class BlowfishEncryptor {

    private static final String ALGORITHM = "Blowfish";
    private String keyString;

    public BlowfishEncryptor(String keyString) {
        this.keyString = keyString;
    }

    public void encrypt(File inputFile, File outputFile) {
        try {
            doCrypto(Cipher.ENCRYPT_MODE, inputFile, outputFile);
            System.out.println("File encrypted successfully!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void decrypt(File inputFile, File outputFile) {
        try {
            doCrypto(Cipher.DECRYPT_MODE, inputFile, outputFile);
            System.out.println("File decrypted successfully!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void doCrypto(int cipherMode, File inputFile, File outputFile) throws Exception {

        Key secretKey = new SecretKeySpec(keyString.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(cipherMode, secretKey);

        FileInputStream inputStream = new FileInputStream(inputFile);
        byte[] inputBytes = new byte[(int) inputFile.length()];
        inputStream.read(inputBytes);

        byte[] outputBytes = cipher.doFinal(inputBytes);

        FileOutputStream outputStream = new FileOutputStream(outputFile);
        outputStream.write(outputBytes);

        inputStream.close();
        outputStream.close();
    }
}
