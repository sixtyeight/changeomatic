package at.metalab.changeomatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private final static ObjectMapper om = new ObjectMapper();

	@JsonInclude(Include.NON_NULL)
	public static class ChangeomaticJson {
		public String msgId;
		public String event;
		public Integer channel;
		public Integer amount;
		public List<Integer> inhibitedChannels;

		public String stringify() {
			try {
				return om.writeValueAsString(this);
			} catch (Exception exception) {
				throw new RuntimeException("stringify failed", exception);
			}
		}

		public static ChangeomaticJson parse(String json) {
			try {
				return om.readValue(json, ChangeomaticJson.class);
			} catch (Exception exception) {
				throw new RuntimeException("parse failed", exception);
			}
		}
	}

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
		public String channels;

		public String stringify() {
			try {
				return om.writeValueAsString(this).replaceAll(" ", "");
			} catch (Exception exception) {
				throw new RuntimeException("stringify failed", exception);
			}
		}

		public static KassomatJson parse(String json) {
			try {
				return om.readValue(json, KassomatJson.class);
			} catch (Exception exception) {
				throw new RuntimeException("parse failed", exception);
			}
		}

	}

	public static void main(String[] args) throws Exception {
		Config c = new Config();
		c.setCodec(org.redisson.client.codec.StringCodec.INSTANCE);
		c.useSingleServer().setAddress("127.0.0.1:6379");

		RedissonClient r = Redisson.create(c);

		final RTopic<String> changeomaticEvent = r
				.getTopic("changeomatic-event");
		changeomaticEvent.publish(createChangeomaticEvent("starting-up")
				.stringify());

		final RTopic<String> hopperRequest = r.getTopic("hopper-request");
		final RTopic<String> hopperResponse = r.getTopic("hopper-response");
		final RTopic<String> hopperEvent = r.getTopic("hopper-event");

		final RTopic<String> validatorEvent = r.getTopic("validator-event");
		final RTopic<String> validatorRequest = r.getTopic("validator-request");

		// disable all channels (this will also be done by smart
		// payout by default in the future)
		validatorRequest.publish(inhibitAllChannels().stringify());

		final Map<Integer, Boolean> inhibits = new HashMap<Integer, Boolean>();
		inhibits.put(1, true); // 5 euro
		inhibits.put(2, true); // 10 euro
		inhibits.put(3, true); // 20 euro
		inhibits.put(4, true); // 50 euro (disabled)
		inhibits.put(5, true); // 100 euro (disabled)
		inhibits.put(6, true); // 200 euro (disabled)
		inhibits.put(7, true); // 500 euro (disabled)
		inhibits.put(8, true); // unused

		// initial money check
		submitAllTestPayouts(inhibits, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		validatorEvent.addListener(new MessageListener<String>() {

			public void onMessage(String channel, String strMessage) {
				try {
					KassomatJson message = KassomatJson.parse(strMessage);

					if ("credit".equals(message.event)) {
						changeomaticEvent
								.publishAsync(createChangeomaticNoteCredited(
										Integer.parseInt(message.channel),
										message.amount).stringify());

						validatorRequest.publishAsync(inhibitAllChannels()
								.stringify());

						hopperRequest.publishAsync(doPayout(message.amount)
								.stringify());
					}
				} catch (Exception exception) {
					oops("validator-event-listener", exception);
				}
			}
		});

		hopperEvent.addListener(new MessageListener<String>() {
			public void onMessage(String channel, String strMessage) {
				try {
					KassomatJson message = KassomatJson.parse(strMessage);

					switch (message.event) {
					case "dispensing":
						validatorRequest.publishAsync(inhibitAllChannels()
								.stringify());
						break;
					case "cashbox paid":
						changeomaticEvent
								.publishAsync(createChangeomaticPayoutCompleted()
										.stringify());
					case "coin credit":
						submitAllTestPayouts(inhibits, hopperRequest,
								hopperResponse, validatorRequest,
								changeomaticEvent);

						break;
					}
				} catch (Exception exception) {
					oops("hopper-event-listener", exception);
				}
			}
		});

		System.out.println("change-o-matic is open for business :D");
		System.out.println("press enter to exit...");
		System.in.read();

		r.shutdown();
	}

	private static void submitAllTestPayouts(
			final Map<Integer, Boolean> inhibits,
			final RTopic<String> hopperRequest,
			final RTopic<String> hopperResponse,
			final RTopic<String> validatorRequest,
			final RTopic<String> changeomaticEvent) {
		submitTestPayout(inhibits, 500, 1, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		submitTestPayout(inhibits, 1000, 2, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		submitTestPayout(inhibits, 2000, 3, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		submitTestPayout(inhibits, 5000, 4, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		submitTestPayout(inhibits, 10000, 5, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		submitTestPayout(inhibits, 20000, 6, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		submitTestPayout(inhibits, 50000, 7, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);
	}

	private static void submitTestPayout(final Map<Integer, Boolean> inhibits,
			final int amount, final int channel,
			final RTopic<String> hopperRequest,
			final RTopic<String> hopperResponse,
			final RTopic<String> validatorRequest,
			final RTopic<String> changeomaticEvent) {
		final KassomatJson tp = testPayout(amount);
		final KassomatRequestCallback w = new KassomatRequestCallback(tp.msgId) {

			@Override
			public void handleMessage(String topic, KassomatJson message) {
				hopperResponse.removeListener(getId());

				synchronized (inhibits) {
					if ("ok".equals(message.result)) {
						inhibits.put(channel, false);
					} else {
						inhibits.put(channel, true);
					}

					List<Integer> channelsToInhibit = new ArrayList<Integer>();
					for (Map.Entry<Integer, Boolean> entry : inhibits
							.entrySet()) {
						if (entry.getValue()) {
							channelsToInhibit.add(entry.getKey());
						}
					}

					changeomaticEvent
							.publishAsync(createChangeomaticInhibitsChanged(
									channelsToInhibit).stringify());

					validatorRequest.publishAsync(inhibitChannels(
							channelsToInhibit).stringify());
				}
			}
		};
		w.setId(hopperResponse.addListener(w));
		hopperRequest.publishAsync(tp.stringify());
	}

	public abstract static class KassomatRequestCallback implements
			MessageListener<String> {

		private String correlId;

		private int id;

		public KassomatRequestCallback(String msgId) {
			this.correlId = msgId;
		}

		public void onMessage(String topic, String strMessage) {
			try {
				KassomatJson message = KassomatJson.parse(strMessage);
				if (correlId.equals(message.correlId)) {
					handleMessage(topic, message);
				}
			} catch (Exception exception) {
				oops("WaitForKassomat", exception);
			}
		}

		public abstract void handleMessage(String topic, KassomatJson message);

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

	}

	private static void oops(String text, Exception exception) {
		System.out.println("oops: " + exception.getMessage());
		exception.printStackTrace();
	}

	private static ChangeomaticJson createChangeomaticEvent(String event) {
		ChangeomaticJson c = new ChangeomaticJson();
		c.event = event;
		c.msgId = UUID.randomUUID().toString();

		return c;
	}

	private static ChangeomaticJson createChangeomaticNoteCredited(int channel,
			int amount) {
		ChangeomaticJson c = createChangeomaticEvent("note-credited");
		c.amount = amount;
		c.channel = channel;

		return c;
	}

	private static ChangeomaticJson createChangeomaticPayoutCompleted() {
		ChangeomaticJson c = createChangeomaticEvent("payout-completed");

		return c;
	}

	private static ChangeomaticJson createChangeomaticInhibitsChanged(
			List<Integer> inhibitedChannels) {
		ChangeomaticJson c = createChangeomaticEvent("inhibits-changed");
		c.inhibitedChannels = inhibitedChannels;

		return c;
	}

	private static KassomatJson createSmartPayoutRequest(String cmd) {
		KassomatJson k = new KassomatJson();
		k.cmd = cmd;
		k.msgId = UUID.randomUUID().toString();

		return k;
	}

	private static KassomatJson doPayout(int amount) {
		KassomatJson k = createSmartPayoutRequest("do-payout");
		k.amount = amount;

		return k;
	}

	private static KassomatJson testPayout(int amount) {
		KassomatJson k = createSmartPayoutRequest("test-payout");
		k.amount = amount;

		return k;
	}

	private static KassomatJson inhibitAllChannels() {
		KassomatJson k = createSmartPayoutRequest("inhibit-channels");
		k.channels = "1,2,3,4,5,6,7,8";
		return k;
	}

	private static KassomatJson inhibitChannels(List<Integer> channels) {
		KassomatJson k = createSmartPayoutRequest("inhibit-channels");
		StringBuilder s = new StringBuilder();
		for (Integer channel : channels) {
			if (s.length() > 0) {
				s.append(",");
			}
			s.append(channel);
		}
		k.channels = s.toString();
		return k;
	}

}