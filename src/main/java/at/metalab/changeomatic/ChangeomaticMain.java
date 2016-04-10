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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChangeomaticMain {

	private final static ObjectMapper om = new ObjectMapper();

	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true) 	
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
	@JsonIgnoreProperties(ignoreUnknown = true) 	
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
		public String id;

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

		final ChangeomaticFrame changeomaticFrame = new ChangeomaticFrame();
		startupGui(changeomaticFrame);

		synchronized (changeomaticFrame) {
			changeomaticFrame.wait();
			// GUI available
			changeomaticEvent.publish(createChangeomaticEvent("started")
					.stringify());
		}
		
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

		changeomaticFrame.hintPleaseWait();

		// initial money check
		submitAllTestPayouts(changeomaticFrame, inhibits, hopperRequest, hopperResponse,
				validatorRequest, changeomaticEvent);

		// handle events which have happened in the banknote validator
		validatorEvent.addListener(new MessageListener<String>() {

			public void onMessage(String channel, String strMessage) {
				try {
					KassomatJson message = KassomatJson.parse(strMessage);

					switch (message.event) {
					case "credit":
						validatorRequest.publishAsync(inhibitAllChannels()
								.stringify());
						validatorRequest.publishAsync(disable().stringify());

						hopperRequest.publishAsync(doPayout(message.amount)
								.stringify());
						break;

					case "read":
						inhibitAll(inhibits);
						break;
						
					case "reading":
						changeomaticFrame.hintPleaseWait();
						break;
						
					case "rejecting":
						changeomaticFrame.hintSorry();
						break;
						
					case "rejected":
						if(inhibitedChannels(inhibits) == inhibits.size()) {
							// we can't change anything at the moment
							changeomaticFrame.hintOhNo();
						} else {
							changeomaticFrame.hintInsertNote();
						}
						break;
					}
				} catch (Exception exception) {
					oops("validator-event-listener", exception);
				}
			}
		});

		// handle events which have happened in the coin hopper
		hopperEvent.addListener(new MessageListener<String>() {
			public void onMessage(String channel, String strMessage) {
				try {
					KassomatJson message = KassomatJson.parse(strMessage);

					switch (message.event) {
					case "disabled":
						hopperRequest.publishAsync(enable().stringify());
						break;
						
					case "dispensing":
						changeomaticFrame.hintDispensing();
						break;

					case "floated":
					case "cashbox paid":
						changeomaticFrame.hintPleaseWait();

						validatorRequest.publishAsync(inhibitAllChannels()
								.stringify());
						
						submitAllTestPayouts(changeomaticFrame, inhibits, hopperRequest,
								hopperResponse, validatorRequest,
								changeomaticEvent);

						validatorRequest.publishAsync(enable().stringify());
						break;

					case "coin credit":
						submitAllTestPayouts(changeomaticFrame, inhibits, hopperRequest,
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
			final ChangeomaticFrame changeomaticFrame,
			final Map<Integer, Boolean> inhibits,
			final RTopic<String> hopperRequest,
			final RTopic<String> hopperResponse,
			final RTopic<String> validatorRequest,
			final RTopic<String> changeomaticEvent) {
		submitTestPayout(changeomaticFrame, inhibits, 500, 1, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, inhibits, 1000, 2, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, inhibits, 2000, 3, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, inhibits, 5000, 4, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		/*
		submitTestPayout(changeomaticFrame, inhibits, 10000, 5, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, inhibits, 20000, 6, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);

		submitTestPayout(changeomaticFrame, inhibits, 50000, 7, hopperRequest,
				hopperResponse, validatorRequest, changeomaticEvent);
		*/
	}

	private static final Map<Integer, Integer> lastUpdated = new HashMap<Integer, Integer>();

	private static int tpCounter = 0;

	private static synchronized void submitTestPayout(
			final ChangeomaticFrame changeomaticFrame,
			final Map<Integer, Boolean> inhibits, final int amount,
			final int channel, final RTopic<String> hopperRequest,
			final RTopic<String> hopperResponse,
			final RTopic<String> validatorRequest,
			final RTopic<String> changeomaticEvent) {
		final KassomatJson tp = testPayout(amount);
		final KassomatRequestCallback w = new KassomatRequestCallback(tp.msgId) {

			@Override
			public void handleMessage(String topic, KassomatJson message) {
				hopperResponse.removeListener(getId());

				synchronized (inhibits) {
					Integer channelLastUpdated = lastUpdated.get(channel);
					if (channelLastUpdated == null) {
						channelLastUpdated = getTpCount();
						lastUpdated.put(channel, channelLastUpdated);
					}

					if (getTpCount() < channelLastUpdated) {
						// outdated response
						System.out.println("skipping outdated response");
						return;
					}

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

					if(inhibitedChannels(inhibits) == inhibits.size()) {
						// we can't change anything at the moment
						changeomaticFrame.hintOhNo();
					} else {
						changeomaticFrame.hintInsertNote();
					}
					
					changeomaticFrame.updateInhibits(channelsToInhibit);
					
					validatorRequest.publishAsync(inhibitChannels(
							channelsToInhibit).stringify());
				}
			}
		};

		tpCounter++;
		w.setTpCount(tpCounter);

		w.setId(hopperResponse.addListener(w));
		hopperRequest.publishAsync(tp.stringify());
	}

	private static int inhibitedChannels(Map<Integer, Boolean> inhibits) {
		int i = 0;
		for(Boolean inhibited : inhibits.values()) {
			if(inhibited) {
				i++;
			}
		}
		return i;
	}
	
	public abstract static class KassomatRequestCallback implements
			MessageListener<String> {

		private String correlId;

		private int id;

		private int tpCount;

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
				oops("KassomatRequestCallback", exception);
			}
		}

		public abstract void handleMessage(String topic, KassomatJson message);

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public int getTpCount() {
			return tpCount;
		}

		public void setTpCount(int tpCount) {
			this.tpCount = tpCount;
		}
	}
	
	private static void inhibitAll(Map<Integer, Boolean> inhibits) {
		for(Map.Entry<Integer, Boolean> entry : inhibits.entrySet()) {
			entry.setValue(true);
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

	private static KassomatJson disable() {
		KassomatJson k = createSmartPayoutRequest("disable");
		return k;
	}
	
	private static KassomatJson enable() {
		KassomatJson k = createSmartPayoutRequest("enable");
		return k;
	}
	
	private static void startupGui(ChangeomaticFrame changeomaticFrame) {
		/* Set the Nimbus look and feel */
		// <editor-fold defaultstate="collapsed"
		// desc=" Look and feel setting code (optional) ">
		/*
		 * If Nimbus (introduced in Java SE 6) is not available, stay with the
		 * default look and feel. For details see
		 * http://download.oracle.com/javase
		 * /tutorial/uiswing/lookandfeel/plaf.html
		 */
		try {
			for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager
					.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					javax.swing.UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch (ClassNotFoundException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		} catch (InstantiationException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		} catch (javax.swing.UnsupportedLookAndFeelException ex) {
			java.util.logging.Logger.getLogger(
					ChangeomaticFrame.class.getName()).log(
					java.util.logging.Level.SEVERE, null, ex);
		}
		// </editor-fold>
		// </editor-fold>
		
		/* Create and display the form */
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				changeomaticFrame.setVisible(true);
				synchronized (changeomaticFrame) {
					changeomaticFrame.notify();
				}
			}
		});
	}
}