package com.arthurfaby.fixmecommon.pipeline;

public interface Handler<T> {
    HandlerResult execute(T ctx) ;
}
