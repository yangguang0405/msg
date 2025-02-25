package com.example.moonshotsignal;

import android.util.Log;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

import java.io.IOException;

public class TelegramHelper {

    private static final String TAG = "TelegramHelper";
    private static final String CHAT_ID = "-1002443099892";
    private static final String BOT_TOKEN = "7880290378:AAH6BoR5ddtP5SJGhpoLzZXFqTfefbMoqWk";
    private static final TelegramBot bot = new TelegramBot(BOT_TOKEN);
    private static final TelegramHelper instance = new TelegramHelper();
    private TelegramHelper() {


        // Register for updates
        bot.setUpdatesListener(updates -> {
            // ... process updates
            // return id of last processed update or confirm them all
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
// Create Exception Handler
        }, e -> {
            if (e.response() != null) {
                // got bad response from telegram
                e.response().errorCode();
                e.response().description();
            } else {
                // probably network error
                e.printStackTrace();
            }
        });
    }

    public static void sendMessage(String msg) {
        instance.sendMessageInternal(msg);
    }

    private void sendMessageInternal(String msg) {
        // Create your bot passing the token received from @BotFather
        SendMessage request = new SendMessage(CHAT_ID, msg)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .disableNotification(true);

        bot.execute(request, new Callback<SendMessage, SendResponse>() {
            @Override
            public void onResponse(SendMessage request, SendResponse response) {

                boolean ok = response.isOk();
                Message message = response.message();
                if (ok) {
                    Log.e("Telegram", "Sending telegram notification failed. " + message == null ? "" : message.text());
                } else {
                    Log.i("Telegram", "Sending telegram notification succeeded. " + message == null ? "" : message.text());
                }
            }

            @Override
            public void onFailure(SendMessage request, IOException e) {

            }
        });
    }
}
