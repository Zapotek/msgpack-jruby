package org.msgpack.jruby;


import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyString;
import org.jruby.RubyObject;
import org.jruby.RubyHash;
import org.jruby.RubyStringIO;
import org.jruby.RubyNumeric;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.util.IOInputStream;

import static org.jruby.runtime.Visibility.PRIVATE;


@JRubyClass(name="MessagePack::Unpacker")
public class Unpacker extends RubyObject {
  private IRubyObject stream;
  private IRubyObject data;
  
  public Unpacker(Ruby runtime, RubyClass type) {
    super(runtime, type);
  }

  static class UnpackerAllocator implements ObjectAllocator {
    private MessagePack msgPack;
      
    public IRubyObject allocate(Ruby runtime, RubyClass klass) {
      return new Unpacker(runtime, klass);
    }
  }

  @JRubyMethod(name = "initialize", optional = 1, visibility = PRIVATE)
  public IRubyObject initialize(ThreadContext ctx, IRubyObject[] args) {
    if (args.length == 1) {
      setStream(ctx, args[0]);
    }
    return this;
  }

  @JRubyMethod(required = 2)
  public IRubyObject execute(ThreadContext ctx, IRubyObject data, IRubyObject offset) {
    return executeLimit(ctx, data, offset, null);
  }

  @JRubyMethod(name = "execute_limit", required = 3)
  public IRubyObject executeLimit(ThreadContext ctx, IRubyObject data, IRubyObject offset, IRubyObject limit) {
    this.data = null;
    try {
      int jOffset = RubyNumeric.fix2int(offset);
      int jLimit = -1;
      if (limit != null) {
        jLimit = RubyNumeric.fix2int(limit);
      }
      byte[] bytes = data.asString().getBytes();
      MessagePackBufferUnpacker localBufferUnpacker = new MessagePackBufferUnpacker(msgPack, bytes.length);
      localBufferUnpacker.wrap(bytes, jOffset, jLimit == -1 ? bytes.length - jOffset : jLimit);
      this.data = rubyObjectUnpacker.valueToRubyObject(ctx.getRuntime(), localBufferUnpacker.readValue(), options);
      return ctx.getRuntime().newFixnum(jOffset + localBufferUnpacker.getReadByteCount());
    } catch (IOException ioe) {
      // TODO: how to throw Ruby exceptions?
      return ctx.getRuntime().getNil();
    }
  }

  @JRubyMethod(name = "data")
  public IRubyObject getData(ThreadContext ctx) {
    if (data == null) {
      return ctx.getRuntime().getNil();
    } else {
      return data;
    }
  }

  @JRubyMethod(name = "finished?")
  public IRubyObject finished_q(ThreadContext ctx) {
    return data == null ? ctx.getRuntime().getFalse() : ctx.getRuntime().getTrue();
  }

  @JRubyMethod(required = 1)
  public IRubyObject feed(ThreadContext ctx, IRubyObject data) {
    streamUnpacker = null;
    byte[] bytes = data.asString().getBytes();
    if (bufferUnpacker == null) {
      bufferUnpacker = new MessagePackBufferUnpacker(msgPack);
      unpackerIterator = bufferUnpacker.iterator();
    }
    bufferUnpacker.feed(bytes);
    return ctx.getRuntime().getNil();
  }

  @JRubyMethod(name = "feed_each", required = 1)
  public IRubyObject feedEach(ThreadContext ctx, IRubyObject data, Block block) {
    feed(ctx, data);
    each(ctx, block);
    return ctx.getRuntime().getNil();
  }
  
  @JRubyMethod
  public IRubyObject each(ThreadContext ctx, Block block) {
    MessagePackUnpacker localUnpacker = null;
    if (bufferUnpacker == null && streamUnpacker != null) {
      localUnpacker = streamUnpacker;
    } else if (bufferUnpacker != null) {
      localUnpacker = bufferUnpacker;
    } else {
      return ctx.getRuntime().getNil();
    }
    if (block.isGiven()) {
      while (unpackerIterator.hasNext()) {
        Value value = unpackerIterator.next();
        IRubyObject rubyObject = rubyObjectUnpacker.valueToRubyObject(ctx.getRuntime(), value, options);
        block.yield(ctx, rubyObject);
      }
      return ctx.getRuntime().getNil();
    } else {
      return callMethod(ctx, "to_enum");
    }
  }

  @JRubyMethod
  public IRubyObject fill(ThreadContext ctx) {
    return ctx.getRuntime().getNil();
  }

  @JRubyMethod
  public IRubyObject reset(ThreadContext ctx) {
    if (bufferUnpacker != null) {
      bufferUnpacker.reset();
    }
    if (streamUnpacker != null) {
      streamUnpacker.reset();
    }
    return ctx.getRuntime().getNil();
  }

  @JRubyMethod(name = "stream")
  public IRubyObject getStream(ThreadContext ctx) {
    if (stream == null) {
      return ctx.getRuntime().getNil();
    } else {
      return stream;
    }
  }

  @JRubyMethod(name = "stream=", required = 1)
  public IRubyObject setStream(ThreadContext ctx, IRubyObject stream) {
    bufferUnpacker = null;
    this.stream = stream;
    if (stream instanceof RubyStringIO) {
      // TODO: RubyStringIO returns negative numbers when read through IOInputStream#read
      IRubyObject str = ((RubyStringIO) stream).string();
      byte[] bytes = ((RubyString) str).getBytes();
      streamUnpacker = new MessagePackUnpacker(msgPack, new ByteArrayInputStream(bytes));
    } else {
      streamUnpacker = new MessagePackUnpacker(msgPack, new IOInputStream(stream));
    }
    unpackerIterator = streamUnpacker.iterator();
    return getStream(ctx);
  }
}