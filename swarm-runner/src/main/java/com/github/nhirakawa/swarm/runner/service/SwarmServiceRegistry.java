package com.github.nhirakawa.swarm.runner.service;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SwarmServiceRegistry {

	private final List<SwarmService> services = new ArrayList<>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	public void add(SwarmService service) {
		lock.writeLock().lock();
		try {
			services.add(service);
		} finally {
			lock.writeLock().unlock();
		}
	}

	public List<StateSnapshot> getSnapshots() {
		lock.readLock().lock();
		try {
			return services.stream().map(SwarmService::getSnapshot).toList();
		} finally {
			lock.readLock().unlock();
		}
	}

	public int size() {
		lock.readLock().lock();
		try {
			return services.size();
		} finally {
			lock.readLock().unlock();
		}
	}

	public List<SwarmService> toList() {
		lock.readLock().lock();
		try {
			return List.copyOf(services);
		} finally {
			lock.readLock().unlock();
		}
	}
}
