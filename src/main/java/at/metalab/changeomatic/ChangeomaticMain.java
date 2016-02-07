package at.metalab.changeomatic;

import java.util.UUID;

import org.redisson.Config;
import org.redisson.Redisson;
import org.redisson.RedissonClient;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChangeomaticMain {

	@JsonInclude(Include.NON_NULL)
	public static class KassomatJson {
		public String cmd;
		public String event;
		public String msgId;
		public String correlId;
		public Integer amount;
		public String error;
		public String result;
		public String cc;
		public String channel;
	}

	private final static ObjectMapper om = new ObjectMapper();

	public static void main(String[] args) throws Exception {
		Config c = new Config();
		c.setCodec(org.redisson.client.codec.StringCodec.INSTANCE);
		c.useSingleServer().setAddress("127.0.0.1:6379");

		RedissonClient r = Redisson.create(c);

		final RTopic<String> hopperRequest = r.getTopic("hopper-request");
		final RTopic<String> validatorEvent = r.getTopic("validator-event");

		validatorEvent.addListener(new MessageListener<String>() {

			public void onMessage(String channel, String strMessage) {
				try {
					KassomatJson message = om.readValue(strMessage,
							KassomatJson.class);

					if ("credit".equals(message.event)) {
						System.out.println("banknote credited with amount="
								+ message.amount);

						KassomatJson doPayout = new KassomatJson();

						doPayout.msgId = UUID.randomUUID().toString();
						doPayout.cmd = "do-payout";
						doPayout.amount = message.amount;

						String strDoPayout = om.writeValueAsString(doPayout)
								.replaceAll(" ", "");

						System.out.println("starting coin payout with amount="
								+ doPayout.amount);

						hopperRequest.publishAsync(strDoPayout);
					}
				} catch (Exception exception) {
					System.out.println("oops: " + exception.getMessage());
					exception.printStackTrace();
				}
			}
		});

		System.out.println("press enter to exit...");
		System.in.read();

		r.shutdown();
	}
}