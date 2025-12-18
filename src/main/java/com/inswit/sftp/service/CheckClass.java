package com.inswit.sftp.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.util.Properties;

import org.springframework.stereotype.Service;

@Service
public class CheckClass {

    private final String user = "karix-sftp-user";
    private final String password = "Lm8F";
    private final String host = "secureftp.com";
    private final int port = 22;    


    public void checkConnection() {
        Session session = getSession(user, password);
        if (session != null && session.isConnected()) {
            System.out.println("SFTP Session connected successfully.");
            session.disconnect(); // always disconnect after use
        } else {
            System.out.println("Failed to connect to SFTP session.");
        }
    }

    private Session getSession(String user, String password) {
        JSch jsch = new JSch();
        Session session = null;

        try {
            session = jsch.getSession(user, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");

            // Match server-supported MAC algorithms
//            config.put("mac.c2s", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com");
//            config.put("mac.s2c", "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com");

            // Optional: Other algorithms (if needed)
            config.put("kex", "curve25519-sha256,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha1");
            config.put("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr");
            config.put("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr");

            session.setConfig(config);
            session.connect(5000); // 5 seconds timeout
            System.out.println("SFTP Session Created");

        } catch (JSchException e) {
            System.out.println("Could Not Create SFTP Session. Reason: " + e.getMessage());
        }

        return session;
    }


}