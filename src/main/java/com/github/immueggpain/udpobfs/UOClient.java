package com.github.immueggpain.udpobfs;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.ArrayUtils;

import com.github.immueggpain.udpobfs.Launcher.ClientSettings;

public class UOClient {

	private SocketAddress last_app_sockaddr;

	public void run(ClientSettings settings) {
		try {
			// convert password to aes key
			byte[] bytes = settings.password.getBytes(StandardCharsets.UTF_8);
			byte[] byteKey = new byte[16];
			System.arraycopy(bytes, 0, byteKey, 0, Math.min(byteKey.length, bytes.length));
			SecretKeySpec secretKey = new SecretKeySpec(byteKey, "AES");
			// we use 2 ciphers because we want to support encrypt/decrypt full-duplex
			String transformation = "AES/GCM/PKCS5Padding";
			Cipher encrypter = Cipher.getInstance(transformation);
			Cipher decrypter = Cipher.getInstance(transformation);

			// setup sockets
			InetAddress server_addr = InetAddress.getByName(settings.server_ip);
			InetAddress loopback_addr = InetAddress.getByName("127.0.0.1");
			DatagramSocket sapp_s = new DatagramSocket(settings.client_port, loopback_addr);
			DatagramSocket cserver_s = new DatagramSocket();

			// start working threads
			Thread transfer_c2s_thread = Util.execAsync("transfer_c2s",
					() -> transfer_c2s(sapp_s, encrypter, secretKey, server_addr, settings.server_port, cserver_s));
			Thread transfer_s2c_thread = Util.execAsync("transfer_s2c",
					() -> transfer_s2c(cserver_s, decrypter, secretKey, sapp_s));

			transfer_c2s_thread.join();
			transfer_s2c_thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void transfer_c2s(DatagramSocket sapp_s, Cipher encrypter, Key secretKey, InetAddress server_addr,
			int server_port, DatagramSocket cserver_s) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				sapp_s.receive(p);
				last_app_sockaddr = p.getSocketAddress();
				byte[] encrypted = Util.encrypt(encrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(encrypted);
				p.setAddress(server_addr);
				p.setPort(server_port);
				cserver_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void transfer_s2c(DatagramSocket cserver_s, Cipher decrypter, Key secretKey, DatagramSocket sapp_s) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				cserver_s.receive(p);
				byte[] decrypted = Util.decrypt(decrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(decrypted);
				p.setSocketAddress(last_app_sockaddr);
				sapp_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static byte[] encrypt(Cipher encrypter, Key secretKey, byte[] input, int offset, int length)
			throws GeneralSecurityException {
		// we need init every time because we want random iv
		encrypter.init(Cipher.ENCRYPT_MODE, secretKey);
		byte[] iv = encrypter.getIV();
		byte[] encrypedBytes = encrypter.doFinal(input, offset, length);
		return ArrayUtils.addAll(iv, encrypedBytes);
	}

	public static byte[] decrypt(Cipher decrypter, Key secretKey, byte[] input, int offset, int length)
			throws GeneralSecurityException {
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, input, offset, 12);
		decrypter.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
		byte[] decryptedBytes = decrypter.doFinal(input, offset + 12, length - 12);
		return decryptedBytes;
	}

}
