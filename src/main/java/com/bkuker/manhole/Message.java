package com.bkuker.manhole;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.dumbster.smtp.SmtpMessage;

public class Message {
	private static long ID;
	private final long id;
	private final SmtpMessage m;

	public Message(SmtpMessage m) {
		id = ID++;
		this.m = m;
	}

	public long getId() {
		return id;
	}

	public Map<String, List<String>> getHeaders() {
		return m.getHeaderNames().stream().collect(Collectors.toMap(n -> n, n -> m.getHeaderValues(n)));
	}

	public String getSubject() {
		return m.getHeaderValue("Subject");
	}

	public String getBody() {
		return m.getBody();
	}
}
