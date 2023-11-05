import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LittleEndianInputStream {

	private RandomAccessFile stream;

	public LittleEndianInputStream(String filename) throws IOException {
		this.stream = new RandomAccessFile(filename, "r");
	}

	public LittleEndianInputStream(File file) throws IOException {
		this.stream = new RandomAccessFile(file, "r");
	}

	private ByteBuffer getByteBuffer(int size) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(size);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		if (stream.read(buf.array(), 0, buf.capacity()) != buf.capacity()) throw new EOFException();
		return buf;
	}

	public int readInt() throws IOException {
		return getByteBuffer(4).getInt();
	}

	public short readShort() throws IOException {
		return getByteBuffer(2).getShort();
	}

	public byte readByte() throws IOException {
		return getByteBuffer(1).get();
	}

	public int readUnsignedByte() throws IOException {
		return getByteBuffer(1).get() & 0xFF;
	}

	public float readFloat() throws IOException {
		return getByteBuffer(4).getFloat();
	}

	public byte[] readBytes(int n) throws IOException {
		byte[] buffer = new byte[n];
		stream.read(buffer);
		return buffer;
	}

	public String readString() throws IOException {
		int length = readShort();
		return readString(length);
	}

	public String readString(int n) throws IOException {
		String s = "";
		
		if (n == 0) return s;
		
		byte[] buffer = readBytes(n);
		
		for (int i = 0; i < n; i++) {
			if (buffer[i] == 0) {
				return s;
			}
			s += (char)(buffer[i] & 0xFF);
		}
		return s;
	}

	public void skip(int n) throws IOException {
		stream.seek(stream.getFilePointer() + n);
	}

	public void seek(int position) throws IOException {
		stream.seek(position);
	}

	public int getPosition() throws IOException {
		return (int) stream.getFilePointer();
	}

	public boolean eof() throws IOException {
		return stream.getFilePointer() == stream.length();
	}

	public String readLine() throws IOException {
		return stream.readLine();
	}

	public void close() throws IOException {
		stream.close();
	}
}
