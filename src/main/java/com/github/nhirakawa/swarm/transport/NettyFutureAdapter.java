package com.github.nhirakawa.swarm.transport;

import java.util.concurrent.CompletableFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class NettyFutureAdapter {

  public static CompletableFuture<Void> of(Future<Void> future) {
    CompletableFuture<Void> completableFuture = new CompletableFuture<>();

    future.addListener(new GenericFutureListener<Future<? super Void>>() {
      @Override
      public void operationComplete(Future<? super Void> future1) {
        if (future1.isSuccess()) {
          completableFuture.complete(null);
        } else if (future1.isCancelled()) {
          completableFuture.cancel(true);
        } else {
          completableFuture.completeExceptionally(future1.cause());
        }
      }
    });

    return completableFuture;

  }
}
