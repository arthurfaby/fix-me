package com.arthurfaby.fixmemarket.cli;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

@Plugin(name = "SplitTerminalAppender", category = "Core", elementType = "appender", printObject = true)
public final class SplitTerminalAppender extends AbstractAppender {

    private SplitTerminalAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout, false, null);
    }

    @PluginFactory
    public static SplitTerminalAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") Filter filter) {
        return new SplitTerminalAppender(name, filter, layout);
    }

    @Override
    public void append(LogEvent event) {
        String formatted = new String(getLayout().toByteArray(event), StandardCharsets.UTF_8);
        for (String line : formatted.split("\n")) {
            if (!line.isEmpty()) {
                SplitTerminal.writeLog(line);
            }
        }
    }
}
