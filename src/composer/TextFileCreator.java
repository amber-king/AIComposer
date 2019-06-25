/**
 * Creates the training text files from the training MIDI files
 */

package composer;
import java.io.File;
import java.io.IOException;
import javax.sound.midi.InvalidMidiDataException;

public class TextFileCreator
{
	
	public static void main(String[] args) throws IOException, InvalidMidiDataException
	{
		// create new FileConverter
		FileConverter fileConverter = new FileConverter();
		
		// create new directory called "txt-training"
		File textTraining = new File("txt-training");
		textTraining.mkdir();
		
		// access files in "midi-training" and store them in an array of Files
		File midiTraining = new File("midi-training");
		File[] midiFiles = midiTraining.listFiles();
		
		// create a text file for each MIDI file in "midi-training" using the textFile() FileConverter method
		for (File midiFile : midiFiles)
		{
			try
			{
				File textFile = fileConverter.textFile(midiFile, textTraining);
				textFile.createNewFile();
			}
			catch (Exception e)
			{
				System.out.println("File not compatible.");
			}
		}
	}
	
}