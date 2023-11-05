import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Scanner;
import java.util.Stack;

public class Decoder {

	public static final boolean DEBUG = false;
	public static final boolean KEEP_BACKUP = true;
	public static final String INDENT = "\t"; // indentation

	public enum Type {
		IDENT, // identifier
		GROUP, // group ( )
		ARRAY, // array [ ]
		INT,  // integer
		FLOAT,
		STRING
	}

	private LittleEndianInputStream stream;
	private FileWriter fileWriter;

	public Decoder(String filename) throws IOException {
		
		// rename original file, add ".bak" at the end
		Path source = Paths.get(filename);
		Files.move(source, source.resolveSibling(filename + ".bak"));
		
		stream = new LittleEndianInputStream(filename + ".bak");
		
		String magic = stream.readString(6);
		if (!magic.equals("CSFFBS")) {
			stream.close();
			
			source = Paths.get(filename + ".bak");
			Files.move(source, source.resolveSibling(filename));
			
			throw new IOException("Not a CSFFBS file!");
		}
		
		fileWriter = new FileWriter(new File(filename));
		
		// unknown values
		stream.readByte(); // 0x00
		stream.readByte(); // 0x7F
		stream.readInt();  // 0x01
		
		int numEntries = stream.readInt();
		int numIdentifiers = stream.readInt();
		int numStrings = stream.readInt();
		
		int offsetEntries = stream.getPosition();
		int offsetIdentifiers = offsetEntries + numEntries * 12;
		
		// first read all identifiers and strings
		stream.seek(offsetIdentifiers);
		
		String[] identifiers = new String[numIdentifiers];
		String[] strings = new String[numStrings];
		
		for (int i = 0; i < numIdentifiers; i++) {
			int length = stream.readInt();
			identifiers[i] = stream.readString(length - 1);
			stream.readByte(); // 00-byte
		}
		
		for (int i = 0; i < numStrings; i++) {
			int length = stream.readInt();
			if (length == 0) { // strings can be empty
				strings[i] = "";
			} else {
				strings[i] = stream.readString(length - 1);
				stream.readByte(); // 00-byte
			}
		}
		
		// seek back and read the entries
		stream.seek(offsetEntries);
		
		int indentLevel = 0;
		// increase indentation after each [ or (
		
		Stack<Type> typeStack = new Stack<Type>();  // keep track of the types of all open arrays[] and groups()
	    Stack<Integer> sizeStack = new Stack<Integer>();  // keep track of the number of remaining elements of all open arrays[] and groups()
	    
	    Type prevType = Type.ARRAY;
		int elementsLeft = 0;
	    
		/* each entry is 12d bytes long
		struct entry {
			int nextEntry; // index of next element on the same indent level?
			int value/size; // value for INT, FLOAT, STRING (= index in string table) / size for GROUP, ARRAY
			short index; // for IDENT, GROUP, ARRAY (= index in identifier table, can be -1 for GROUP and ARRAY)
			short type;
		}
		*/
		
		for (int i = 0; i < numEntries; i++) {
			int nextEntry = stream.readInt();
			stream.skip(6);
			Type type = Type.values()[stream.readShort()];
			
			int index, size, valueInt;
			float valueFloat;
			String identifier;
			
			switch (type) {
			
			case IDENT: // 00
				stream.skip(-4);
				index = stream.readShort();
				stream.skip(2);
				
				printDebug("IDENT\t" + i + "\t" + nextEntry + "\t" + identifiers[index]);
				
				printToken(indentLevel, identifiers[index]);
				
				break;
			
			case GROUP: // 01
				sizeStack.push(elementsLeft - 1);
				typeStack.push(type);
				
				stream.skip(-8);
				size = stream.readInt();
				index = stream.readShort();
				stream.skip(2);
				
				elementsLeft = size;
				
				if (index == -1) {
					identifier = "";
				} else {
					identifier = identifiers[index];
				}
				
				printDebug("GROUP(" + size + ")\t" + i + "\t" + nextEntry + "\t" + identifier);
				
				if (index != -1) {
					printToken(indentLevel, identifier + "\n");
				}
				printToken(indentLevel, "(\n");
				
				indentLevel++;
				break;
			
			case ARRAY: // 02
				sizeStack.push(elementsLeft - 1);
				typeStack.push(type);
				
				stream.skip(-8);
				size = stream.readInt();
				index = stream.readShort();
				stream.skip(2);
				
				elementsLeft = size;
				
				if (index == -1) {
					identifier = "";
				} else {
					identifier = identifiers[index];
				}
				
				printDebug("ARRAY[" + size + "]\t" + i + "\t" + nextEntry  + "\t" + identifier);
				
				if (index != -1) {
					printToken(indentLevel, identifier + "\n");
				}
				printToken(indentLevel, "[\n");
				
				indentLevel++;
				break;
			
			case INT: // 03
				stream.skip(-8);
				valueInt = stream.readInt();
				stream.skip(4);
				
				printDebug("INT\t" + i + "\t" + nextEntry + "\t" + valueInt);
				
				if (prevType == Type.IDENT) {
					printToken(0, " " + valueInt + "\n");
				} else {
					printToken(indentLevel, valueInt + "\n");
				}
				
				elementsLeft--;
				break;
			
			case FLOAT: // 04
				stream.skip(-8);
				valueFloat = stream.readFloat();
				stream.skip(4);
				
				printDebug("FLOAT\t" + i + "\t" + nextEntry + "\t" + valueFloat);
				
				if (prevType == Type.IDENT) {
					printToken(0, " " + floatToString(valueFloat) + "\n");
				} else {
					printToken(indentLevel, floatToString(valueFloat) + "\n");
				}
				
				elementsLeft--;
				break;
			
			case STRING: // 05
				stream.skip(-8);
				index = stream.readInt();
				stream.skip(4);
				
				String s = "\"" + strings[index] + "\"";
				
				printDebug("STRING\t" + i + "\t" + nextEntry + "\t" + s);
				
				if (prevType == Type.IDENT) {
					printToken(0, " " + s + "\n");
				} else {
					printToken(indentLevel, s + "\n");
				}
				
				elementsLeft--;
				break;
			}
			
			prevType = type;
			
			// close brackets
			while (elementsLeft == 0) {
				indentLevel--;
				Type lastType = typeStack.pop();
				if (lastType == Type.ARRAY) {
					printDebug("]");
					printToken(indentLevel, "]\n");
				} else { // Type.GROUP
					printDebug(")");
					printToken(indentLevel, ")\n");
				}
				
				elementsLeft = sizeStack.pop();
			}
		}
		
		stream.close();
		
		fileWriter.close();
		
		if (!KEEP_BACKUP) {
			// delete backup file
			File f = new File(filename + ".bak");
			f.delete();
		}
	}

	private void printToken(int indentLevel, String token) throws IOException {
		for (int i = 0; i < indentLevel; i++) {
			fileWriter.write(INDENT);
		}
		fileWriter.write(token);
	}

	private void printDebug(String s) {
		if (DEBUG) System.out.println(s);
	}

	// nicely format float: round to 6 digits after the decimal point, remove trailing zeros
	private String floatToString(float f) {
		String s = String.format(Locale.US, "%.6f", f);
		int pos = -1;
		for (int i = s.length() - 1; i > s.length() - 6; i--) {
			if (s.charAt(i) == '0') {
				pos = i;
			} else {
				break;
			}
		}
		if (pos != -1) {
			s = s.substring(0, pos);
		}
		return s;
	}

	public static void main(String[] args) {
		
		try {
		
			if (args.length == 0) {
				Scanner scanner = new Scanner(System.in);
		    	System.out.println("Enter filename:");
		    	System.out.print(">");
		    	String filename = scanner.next();
		    	scanner.close();
		    	
		    	File f = new File(filename);
		    	if (!f.exists()) {
		    		System.err.println("File does not exist: " + filename);
		    		return;
		    	}
		    	new Decoder(f.getAbsolutePath());
		    	
			} else {
				
				for (String filename : args) {
					new Decoder(filename);
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
