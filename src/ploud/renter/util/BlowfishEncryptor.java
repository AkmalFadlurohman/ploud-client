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
            System.out.println("Encryption key: " + keyString);
            doCrypto(Cipher.ENCRYPT_MODE, inputFile, outputFile);
            System.out.println("File encrypted successfully!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void decrypt(File inputFile, File outputFile) {
        try {
            System.out.println("Decryption key: " + keyString);
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
        byte[] inputBytes = new byte[round((int) inputFile.length())];
        int bytesRead = inputStream.read(inputBytes);

        byte[] outputBytes = cipher.update(inputBytes);
        //byte[] outputBytes = cipher.doFinal(inputBytes);

        FileOutputStream outputStream = new FileOutputStream(outputFile);
        outputStream.write(outputBytes);
        outputStream.close();
        inputStream.close();
    }

    private int round(int n)
    {
        int a = (n / 8) * 8;

        int b = a + 8;

        return (n - a > b - n)? b : a;
    }
}
