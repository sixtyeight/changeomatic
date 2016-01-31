# changeomatic
Metalab change machine, a proof of concept using the Payout JSON API

#### Example trace, showing the exchange of a 10 Euro note 
```
1454199307.840491 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"reading\"}"
1454199308.841110 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"reading\"}"
1454199309.828995 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"read\",\"amount\":1000,\"channel\":2}"
1454199310.840973 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"stacking\"}"
1454199311.837005 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"stacking\"}"
1454199312.850649 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"credit\",\"amount\":1000,\"channel\":2}"
1454199312.853718 [0 127.0.0.1:42441] "PUBLISH" "hopper-request" "{\"cmd\":\"do-payout\",\"msgId\":\"de21cbee-7bef-4786-935a-0e84231ce916\",\"amount\":1000}"
1454199312.859260 [0 127.0.0.1:42458] "PUBLISH" "hopper-response" "{\"correlId\":\"de21cbee-7bef-4786-935a-0e84231ce916\",\"result\":\"ok\"}"
1454199313.841223 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":0}"
1454199313.841295 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"stacking\"}"
1454199313.841338 [0 127.0.0.1:42458] "PUBLISH" "validator-event" "{\"event\":\"stacked\"}"
1454199314.829951 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":0}"
1454199315.830579 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":100}"
1454199316.840290 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":900}"
1454199317.840784 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"dispensing\",\"amount\":1000}"
1454199318.840375 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"cashbox paid\",\"amount\":0,\"cc\":\"EUR\"}"
1454199318.840404 [0 127.0.0.1:42458] "PUBLISH" "hopper-event" "{\"event\":\"dispensed\",\"amount\":1000}"
```