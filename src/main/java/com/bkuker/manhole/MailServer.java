package com.bkuker.manhole;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;

@Repository
public class MailServer extends Thread {

	private final Logger log = LoggerFactory.getLogger(MailServer.class);
	private final int LIMIT = 10;

	private SimpleSmtpServer server;

	private final LinkedList<Message> messages = new LinkedList<>();

	final Set<Consumer<Message>> listeners = new HashSet<>();

	public MailServer() throws IOException {
		setName("Mail Processing");
		server = SimpleSmtpServer.start(25);
	}

	public synchronized void add(SmtpMessage s) {
		Message m = new Message(s);
		messages.add(m);
		listeners.forEach(l -> l.accept(m));
		if (messages.size() > LIMIT) {
			messages.remove(0);
		}
	}

	public Stream<Message> getMessages() {
		return messages.stream();
	}

	@PostConstruct
	public void start() {
		super.start();
	}

	public void run() {
		while (true) {
			for (SmtpMessage m : server.getReceivedEmails()) {
				log.info("Received email {}", m);
				add(m);
			}
			server.reset();

			try {
				Field f = server.getClass().getDeclaredField("serverSocket");
				f.setAccessible(true);
				ServerSocket ss = (ServerSocket) f.get(server);
				if (ss.isClosed()) {
					log.warn("Server died. Fixin' it.");
					server = SimpleSmtpServer.start(25);
				}
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException
					| IOException e) {
				log.error("Error checking server", e);
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.error("Interrupted waiting for email!", e);
			}
		}
	}

}
