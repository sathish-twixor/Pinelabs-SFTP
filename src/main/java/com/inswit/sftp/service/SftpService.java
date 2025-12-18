package com.inswit.sftp.service;

import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

@Service
public class SftpService {

    private static final Logger logger = Logger.getLogger("SftpService");
    	
    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.username}")
    private String sftpUsername;

    @Value("${sftp.password}")
    private String sftpPassword;

    @Value("${sftp.remote.base-path}")
    private String baseRemotePath;

   
    /**
     * Uploads the Excel file, images folder, and documents folder to the SFTP server.
     */
    public void uploadFoldersToSftp(File excelFile, File imageDir, File docDir, 
    		String dateFolder) {
        ChannelSftp channelSftp = setupJsch();
        try {
            String baseFolder = baseRemotePath + "/" + dateFolder;
            ensureRemoteDirectoryExists(channelSftp, baseFolder);

            // Upload Excel file to the base folder
            channelSftp.cd(baseFolder);
            channelSftp.put(new FileInputStream(excelFile), excelFile.getName());
            logger.info("Uploaded Excel: " + excelFile.getName());

            // Upload images
            if (imageDir.exists() && imageDir.isDirectory()) {
                String imageRemotePath = baseFolder + "/images";
                ensureRemoteDirectoryExists(channelSftp, imageRemotePath);
                for (File file : Objects.requireNonNull(imageDir.listFiles())) {
                    if (file.isFile()) {
                        channelSftp.cd(imageRemotePath);
                        channelSftp.put(new FileInputStream(file), file.getName());
                      //  logger.info("Uploaded image: " + file.getName());
                    }
                }
            }

            // Upload documents
            if (docDir.exists() && docDir.isDirectory()) {
                String docRemotePath = baseFolder + "/documents";
                ensureRemoteDirectoryExists(channelSftp, docRemotePath);
                for (File file : Objects.requireNonNull(docDir.listFiles())) {
                    if (file.isFile()) {
                        channelSftp.cd(docRemotePath);
                        channelSftp.put(new FileInputStream(file), file.getName());
                      //  logger.info("Uploaded document: " + file.getName());
                    }
                }
            }

            logger.info("All files uploaded to SFTP successfully.");

        } catch (Exception e) {
            logger.severe("SFTP folder upload failed: " + e.getMessage());
        } finally {
            channelSftp.exit();
        }
    }

    /**
     * Sets up the JSCH SFTP connection.
     */
    private ChannelSftp setupJsch() {
        try {
            JSch jsch = new JSch();
            Session jschSession = jsch.getSession(sftpUsername, sftpHost, sftpPort);
            jschSession.setPassword(sftpPassword);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");    
            config.put("kex", "curve25519-sha256,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha1");
            config.put("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr");
            config.put("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr");
            
            jschSession.setConfig(config);
            jschSession.connect();

            Channel channel = jschSession.openChannel("sftp");
            channel.connect();

            return (ChannelSftp) channel;
        } catch (Exception e) {
            logger.severe("Failed to establish SFTP connection: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Ensures that the full directory path exists on the remote SFTP server.
     */
    private void ensureRemoteDirectoryExists(ChannelSftp channelSftp, String remoteDir) throws SftpException {
        String[] folders = remoteDir.split("/");
        channelSftp.cd("/");
        for (String folder : folders) {
            if (folder.length() > 0) {
                try {
                    channelSftp.cd(folder);
                } catch (SftpException e) {
                    channelSftp.mkdir(folder);
                    channelSftp.cd(folder);
                }
            }
        }
    }
}
