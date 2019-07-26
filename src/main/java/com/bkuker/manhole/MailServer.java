package com.bkuker.manhole;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.simplejavamail.converter.EmailConverter;
import org.simplejavamail.email.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.helper.SimpleMessageListenerAdapter;
import org.subethamail.smtp.server.SMTPServer;

@Repository
public class MailServer extends Thread implements SimpleMessageListener {

	private final Logger log = LoggerFactory.getLogger(MailServer.class);
	private final int LIMIT = 10;

	private final SMTPServer server;
	private final Session s = Session.getDefaultInstance(new Properties());
	private final LinkedList<Email> messages = new LinkedList<>();

	final Set<Consumer<Email>> listeners = new HashSet<>();

	public MailServer() throws IOException {
		setName("Mail Processing");
		this.server = new SMTPServer(new SimpleMessageListenerAdapter(this));
		this.server.setPort(25);
	}

	@Override
	@PostConstruct
	public void start() {
		this.server.start();
	}

	@Override
	public boolean accept(final String from, final String recipient) {
		log.debug("Accepting mail from {} to {}", from, recipient);
		return true;
	}

	@Override
	public void deliver(final String from, final String recipient, final InputStream data)
			throws TooMuchDataException, IOException {
		// Stolen from SubEthaSMTP Wiser
		log.info("Receiving mail from {} to {}", from, recipient);

		Email email;
		try {
			email = EmailConverter.mimeMessageToEmail(new MimeMessage(s, data));
		} catch (final MessagingException e) {
			throw new IOException(e);
		}

		messages.add(email);
		listeners.forEach(l -> l.accept(email));
		if (messages.size() > LIMIT) {
			messages.remove(0);
		}
	}

	public Stream<Email> getMessages() {
		return messages.stream();
	}

}
