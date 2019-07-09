package com.bkuker.manhole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

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

@Controller
public class RestAPI {

	@Autowired
	private MailServer mailServer;

	private final Map<String, DeferredResult<Message>> waits = new HashMap<>();

	@PostConstruct
	public void init() {
		mailServer.listeners.add(m -> {
			synchronized (waits) {
				m.getHeaders().get("To").forEach(to -> {
					if (waits.containsKey(to)) {
						waits.get(to).setResult(m);
						waits.remove(to);
					}
				});
			}
		});
	}

	@RequestMapping("/mail/{id}")
	@ResponseBody
	Message getMessage(@PathVariable Long id) {
		return mailServer.getMessages().filter(m -> m.getId() == id).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	@RequestMapping("/mail/")
	@ResponseBody
	List<String> getMessages() {
		return mailServer.getMessages().map(m -> ServletUriComponentsBuilder.fromCurrentServletMapping()
				.path("/mail/{id}").buildAndExpand(m.getId()).toString()).collect(Collectors.toList());
	}

	@RequestMapping("/nextMessage")
	@ResponseBody
	DeferredResult<Message> getNextMessage(@RequestParam final String to, @RequestParam final int wait) {
		synchronized (waits) {
			waits.entrySet().removeIf(e -> e.getValue().isSetOrExpired());
			if (waits.containsKey(to)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Someone else already waiting for that address.");
			}
			final DeferredResult<Message> ret = new DeferredResult<>(wait * 1000L);
			ret.onTimeout(() -> ret.setErrorResult(new ResponseStatusException(HttpStatus.NOT_FOUND)));
			waits.put(to, ret);
			return ret;
		}
	}

}
