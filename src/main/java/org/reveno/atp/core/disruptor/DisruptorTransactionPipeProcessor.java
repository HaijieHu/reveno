/** 
 *  Copyright (c) 2015 The original author or authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.reveno.atp.core.disruptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.reveno.atp.api.Configuration.CpuConsumption;
import org.reveno.atp.api.EventsManager.EventMetadata;
import org.reveno.atp.api.commands.EmptyResult;
import org.reveno.atp.api.commands.Result;
import org.reveno.atp.core.api.RestoreableEventBus;
import org.reveno.atp.core.api.TransactionCommitInfo;
import org.reveno.atp.core.engine.processor.TransactionPipeProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.EventFactory;

public class DisruptorTransactionPipeProcessor extends DisruptorPipeProcessor<ProcessorContext> implements TransactionPipeProcessor<ProcessorContext> {

	public DisruptorTransactionPipeProcessor(CpuConsumption cpuConsumption,
			boolean singleProducer, Executor executor) {
		this.cpuConsumption = cpuConsumption;
		this.singleProducer = singleProducer;
		this.executor = executor;
	}
	
	@Override
	public CpuConsumption cpuConsumption() {
		return cpuConsumption;
	}

	@Override
	boolean singleProducer() {
		return singleProducer;
	}

	@Override
	EventFactory<ProcessorContext> eventFactory() {
		return eventFactory;
	}

	@Override
	Executor executor() {
		return executor;
	}

	@Override
	public void startInterceptor() {
		// TODO exception listener that will stop disruptor, mark node Slave, etc.
	}
	
	@Override
	public void sync() {
		CompletableFuture<EmptyResult> res = this.process((c,f) -> c.reset().future(f).abort(null));
		try {
			res.get();
		} catch (Throwable t) {
			log.error("sync", t);
		}
	}

	@Override
	public CompletableFuture<EmptyResult> process(List<Object> commands) {
		return process((e,f) -> e.reset().future(f).addCommands(commands));
	}

	@Override
	public <R> CompletableFuture<Result<? extends R>> execute(Object command) {
		return process((e,f) -> e.reset().future(f).addCommand(command).withResult());
	}
	
	@Override
	public void executeRestore(RestoreableEventBus eventBus, TransactionCommitInfo tx) {
		process((e,f) -> e.reset().restore().transactionId(tx.getTransactionId())
					.eventBus(eventBus).eventMetadata(metadata(tx)).getTransactions()
					.addAll(Arrays.asList(tx.getTransactionCommits())));
	}
	
	protected EventMetadata metadata(TransactionCommitInfo tx) {
		return new EventMetadata(true, tx.getTime());
	}
	
	
	protected final boolean singleProducer;
	protected final CpuConsumption cpuConsumption;
	protected final Executor executor;
	protected static final EventFactory<ProcessorContext> eventFactory = () -> new ProcessorContext();
	private static final Logger log = LoggerFactory.getLogger(DisruptorTransactionPipeProcessor.class);
	
}
