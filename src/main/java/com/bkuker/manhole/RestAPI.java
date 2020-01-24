package com.bkuker.manhole;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.simplejavamail.email.Email;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

@Controller
public class RestAPI {

	@Autowired
	private MailServer mailServer;

	@Autowired
	ObjectMapper om;

	private final Map<String, DeferredResult<Email>> waits = new HashMap<>();
	
	private String getId(Email m) {
		if ( m.getId() != null )
			return m.getId();
		return "m-" + System.identityHashCode(m);
	}

	@PostConstruct
	public void init() {
		mailServer.listeners.add(m -> {
			synchronized (waits) {
				m.getRecipients().stream().map(r -> r.getAddress()).forEach(to -> { // TODO Map recip to string
					if (waits.containsKey(to)) {
						waits.get(to).setResult(m);
						waits.remove(to);
					}
				});
			}
		});

		final SimpleModule module = new SimpleModule();
		module.addSerializer(Email.class, new StdSerializer<Email>(Email.class) {

			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public void serialize(final Email email, final JsonGenerator jgen, final SerializerProvider provider)
					throws IOException, JsonProcessingException {
				jgen.writeStartObject();
				jgen.writeStringField("id", URLEncoder.encode(getId(email), "UTF-8"));
				jgen.writeStringField("subject", email.getSubject());
				jgen.writeStringField("from", email.getFromRecipient().getAddress());
				jgen.writeObjectField("to", email.getRecipients().stream().map(r -> r.getAddress()));
				jgen.writeStringField("body", email.getPlainText());
				jgen.writeStringField("html", email.getHTMLText());
				jgen.writeEndObject();
			}
		});
		om.registerModule(module);
	}

	@RequestMapping("/mail/{id}")
	@ResponseBody
	Email getMessage(@PathVariable final String id) throws UnsupportedEncodingException {
		final Email e = mailServer.getMessages().filter(m -> getId(m).equals(id)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return e;
	}

	@RequestMapping("/mail/")
	@ResponseBody
	List<String> getMessages() {
		return mailServer.getMessages().map(m -> {
			try {
				return ServletUriComponentsBuilder.fromCurrentServletMapping().path("/mail/{id}")
						.buildAndExpand(URLEncoder.encode(getId(m), "UTF-8")).toString();
			} catch (final UnsupportedEncodingException e) {
				throw new Error(e);
			}
		}).collect(Collectors.toList());
	}

	@RequestMapping("/nextMessage")
	@ResponseBody
	DeferredResult<Email> getNextMessage(@RequestParam final String to, @RequestParam final int wait) {
		synchronized (waits) {
			waits.entrySet().removeIf(e -> e.getValue().isSetOrExpired());
			if (waits.containsKey(to)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Someone else already waiting for that address.");
			}
			final DeferredResult<Email> ret = new DeferredResult<>(wait * 1000L);
			ret.onTimeout(() -> ret.setErrorResult(new ResponseStatusException(HttpStatus.NOT_FOUND)));
			waits.put(to, ret);
			return ret;
		}
	}

}
