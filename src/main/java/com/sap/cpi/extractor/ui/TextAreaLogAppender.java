package com.sap.cpi.extractor.ui;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.nio.charset.StandardCharsets;

/**
 * Logback appender that writes log messages to a JavaFX TextArea.
 */
public class TextAreaLogAppender extends AppenderBase<ILoggingEvent> {

    private static TextArea textArea;
    private Encoder<ILoggingEvent> encoder;

    public static void setTextArea(TextArea ta) {
        textArea = ta;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }

    @Override
    public void start() {
        if (encoder != null) {
            encoder.start();
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (textArea == null) {
            return;
        }

        String message;
        if (encoder != null) {
            message = new String(encoder.encode(event), StandardCharsets.UTF_8);
        } else {
            message = event.getFormattedMessage() + "\n";
        }

        Platform.runLater(() -> {
            textArea.appendText(message);
            // Auto-scroll to bottom
            textArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}
