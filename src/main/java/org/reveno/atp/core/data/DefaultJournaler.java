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

package org.reveno.atp.core.data;

import static org.reveno.atp.utils.MeasureUtils.mb;

import java.util.concurrent.atomic.AtomicReference;

import org.reveno.atp.core.api.Journaler;
import org.reveno.atp.core.api.channel.Buffer;
import org.reveno.atp.core.api.channel.Channel;
import org.reveno.atp.core.channel.NettyBasedBuffer;

public class DefaultJournaler implements Journaler {

	@Override
	public void writeData(Buffer entry, boolean endOfBatch) {
		requireWriting();
		
		buffer.writeBytes(entry.getBytes());
		if (endOfBatch) {
			Channel ch = channel.get();
			ch.write(buffer, true);
			buffer.clear();
			
			if (!channel.compareAndSet(ch, ch)) {
				try {
					ch.close();
				} catch (Exception e) { }
			}
		}
	}

	@Override
	public void startWriting(Channel ch) {
		channel.set(ch);
		isWriting = true;
	}

	@Override
	public void stopWriting() {
		isWriting = false;
	}

	@Override
	public void roll(Channel ch) {
		Channel curr = channel.get();
		while (!channel.compareAndSet(curr, ch)) {
			curr = channel.get();
		}
	}
	
	
	protected void requireWriting() {
		if (!isWriting)
			throw new RuntimeException("Journaler must be in writing mode.");
	}
	
	
	private final Buffer buffer = new NettyBasedBuffer(mb(1), true);
	private AtomicReference<Channel> channel = new AtomicReference<Channel>();
	private volatile boolean isWriting = false;
	
}