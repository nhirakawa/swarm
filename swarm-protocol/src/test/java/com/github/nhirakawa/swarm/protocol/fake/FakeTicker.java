package com.github.nhirakawa.swarm.protocol.fake;

import com.google.common.base.Ticker;

public class FakeTicker extends Ticker {

	private long current;

	public FakeTicker() {
		this.current = 0L;
	}

	@Override
	public long read() {
		return current;
	}

	public void write(long current) {
		this.current = current;
	}
}
