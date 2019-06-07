/**
 * Includes methods to convert MIDI files to text files and vice versa
 */

package composer;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import javax.sound.midi.*;

public class FileConverter
{
	
	/**
	 * This method takes a MIDI file and outputs a text file in a readable format for machine learning programs. Each MIDI event from
	 * the file that represents a note on event is extracted, and its note value and length are found and written to the text file.
	 * 
	 * @param midiFile		the MIDI file to be converted into a text file
	 * @param folder		the parent folder of the returned file
	 * @return a text file converted from a MIDI file
	 * @throws IOException
	 * @throws InvalidMidiDataException
	 */
	public File textFile(File midiFile, File folder) throws IOException, InvalidMidiDataException
	{
		// create a new file in folder with the same same as midiFile but with a text extension
		String midiFileName = midiFile.getName();
		File textFile = new File(folder, midiFileName.substring(0, midiFileName.indexOf(".")) + ".txt");
		
		// create a FileWriter for the file textFile
		FileWriter fw = new FileWriter(textFile);
		
		Sequence sequence = MidiSystem.getSequence(midiFile);   // get sequence from midiFile
		Track[] tracks = sequence.getTracks();   // get tracks from sequence
		
		// create list of activeTracks (tracks that contain notes)
		ArrayList<Track> activeTracks = new ArrayList<Track>();
		for (Track track : tracks)
			if (noteFound(track))
				activeTracks.add(track);
		
		Track tempoTrack = tempoTrack(sequence);   // find and set tempoTrack
		ArrayList<int[]> tempos = tempos(tempoTrack);   // create list of tempos from tempoTrack
		
		Sequence newSequence = new Sequence(Sequence.PPQ, 48, 1);   // create new sequence with adjusted resolution
		Track newTrack = changeRes(sortedTrack(activeTracks, newSequence, tempos), newSequence);   // create newTrack that is sorted and with the correct res
		
		// iterate through all ticks in newTrack
		int index = 0;
		for (int i = 0; i <= newTrack.ticks(); i++)
		{
			String tick = "";   // create an empty string for each tick
			
			// get the notes of the tick (if there are any)
			while (index < newTrack.size() && newTrack.get(index).getTick() == i)
			{
				if (newTrack.get(index).getMessage().getMessage()[2] != 0)   // only get note if velocity is not 0
				{
					MidiEvent event = newTrack.get(index);
					MidiMessage message = event.getMessage();
					
					byte[] data = message.getMessage();
					int note = data[1]-12;   // get note (note: subtract octave)
					
					long length = 1;
					int endIndex = index + 1;
					boolean noteEndFound = false;
					while (endIndex < newTrack.size() && !noteEndFound)   // find the corresponding note end (the same note with velocity 0)
					{
						if (newTrack.get(endIndex).getMessage().getMessage()[1]-12 == note
							&& newTrack.get(endIndex).getMessage().getMessage()[2] == 0)
						{
							length = newTrack.get(endIndex).getTick() - event.getTick();   // find the length of the note by subtracting the end index from the start index
							noteEndFound = true;
						}
						endIndex++;
					}
					
					char noteChar = (char) (note + 13);   // convert note to a character
					String lengthStr = "" + length;   // convert length to a String
					while (lengthStr.length() < 3)   // pad length with 0s to make it 3 characters
						lengthStr = "0" + lengthStr;
				
					tick += noteChar + lengthStr;   // add the note and length Strings to tick
					
					// check if the note is the last note (that is on) in the tick by checking the rest of the events in the tick (if there are any)
					int nextIndex = index + 1;
					boolean tickEnd = true;
					while (nextIndex < newTrack.size() && newTrack.get(index).getTick() == newTrack.get(nextIndex).getTick())
					{
						if (newTrack.get(nextIndex).getMessage().getMessage()[2] != 0)
							tickEnd = false;
						nextIndex++;
					}
					if (!tickEnd)   // add an exclamation mark if the note is not the last note of the tick
						tick += "!";
				}
				index++;
			}
			
			// if there are no notes for the tick, add a tilde
			if (tick.equals(""))
				tick = "~";
			
			// write the tick String to the text file
			fw.write(tick + " ");
		}
		
		fw.close();
		
		return textFile;
	}
	
	/**
	 * This method loops through all the MIDI events within the input track, and returns true if a note is
	 * found. A MIDI event represents a note that is on if the message status byte is 0x90, or 144. These MIDI events
	 * always have a MIDI message length of 3 bytes.
	 * 
	 * @param track		the track that is being checked
	 * @return true if the track contains a note, false otherwise
	 */
	public static boolean noteFound(Track track)
	{
		for (int i = 0; i < track.size(); i++)
			if (track.get(i).getMessage().getLength() == 3 && track.get(i).getMessage().getStatus() == 144)
				return true;
		
		return false;
	}
	
	/**
	 * This method takes in an array of active tracks and outputs a track that contains only note on MIDI events.
	 * The resultant track has it events arranged in chronological order based on tick value. If there is more than one
	 * event per tick, the events are arranged in order of increasing note value. The tick values of the track that this
	 * method returns have also been adjusted according to the tempos, which are input as an ArrayList. Instead of having
	 * MIDI meta events to change the tempo throughout the track, the resultant track has a tempo of 500,000 microseconds per
	 * quarter note (equivalent to 120 bpm) and the tick length of each note is compressed or stretched accordingly.
	 * <br>
	 * This method uses recursion to sort the tracks, since there is a varying number of active tracks for each file
	 * (typically 2 or 3, though). If the number of active tracks is not 1 or 2, tracks is split in half and each track array is
	 * passed through the method, until ultimately, an array of two tracks is being sorted. When two tracks are being sorted, the
	 * program simultaneously iterates through both tracks, adding the event with the lower tick value to the track.
	 * <br>
	 * PRECONDITION:	each track in tracks has its events arranged in chronological order based on tick value
	 * 
	 * @param tracks		the active tracks that are to be combined into one track
	 * @param sequence		the sequence in which the returned track is to be created
	 * @param tempos		an array holding the tempos of the original sequence (in microseconds per quarter note)
	 * 							and the MIDI tick at which the tempo change occurs
	 * @return a sorted track
	 */
	public static Track sortedTrack(ArrayList<Track> tracks, Sequence sequence, ArrayList<int[]> tempos)
	{	
		if (tracks.size() == 1 || tracks.size() == 2)
		{
			ArrayList<MidiEvent> events = new ArrayList<MidiEvent>();
			Track newTrack = sequence.createTrack();
			
			// if tracks size is 1, all events in the track are added to events
			if (tracks.size() == 1)
			{
				for (int i = 0; i < tracks.get(0).size(); i++)
						events.add(tracks.get(0).get(i));
			}
			// if tracks size is 2, the events from both tracks are added by looking to see which tick is lower
			else
			{
				Track track1 = tracks.get(0);
				Track track2 = tracks.get(1);
				
				int i1 = 0;   // track 1 index
				int i2 = 0;   // track 2 index
				
				// adds the event with the lowest tick
				while (i1 < track1.size() && i2 < track2.size())
				{
					if (track1.get(i1).getTick() <= track2.get(i2).getTick())
					{
						events.add(track1.get(i1));
						i1++;
					}
					else
					{
						events.add(track2.get(i2));
						i2++;
					}
				}
				
				// adds remaining elements in track1 or track2 (if there are any)
				while (i1 < track1.size())
				{
					events.add(track1.get(i1));
					i1++;
				}
				while (i2 < track2.size())
				{
					events.add(track2.get(i2));
					i2++;
				}
			}
			
			// orders notes with the same tick in ascending order
			int index = 0;
			while (index < events.size())
			{
				// firstIndex is the index of the first note of the tick, index is one more than the index of the last note of the tick
				int firstIndex = index;
				
				// find the last index
				while (index < events.size() && events.get(firstIndex).getTick() == events.get(index).getTick())
					index++;
				
				// insertion sort used to organize all events between firstIndex and index by note
				for (int j = firstIndex + 1; j < index; j++)
				{
					MidiEvent temp = events.get(j);
					int i = j;
					while (i > firstIndex && temp.getMessage().getMessage()[1] < events.get(i-1).getMessage().getMessage()[1])
					{
						events.set(i, events.get(i-1));
						i--;
					}
					events.set(i, temp);
				}
			}
			
			int tempo = 500000;   // default tempo (120 bpm)
			int temposIndex = 0;
			long tick = 0;
			long prevTick = 0;
			long prevTickMoved = prevTick;
			double tickAdjustment;
			
			long shift = -1;   // shift is the number of ticks to shift the notes, so that the first note is played at tick 0
			for (MidiEvent event : events)
			{
				// get the tempo for the current tick (i.e. the most recent tempo)
				while (temposIndex < tempos.size() && tempos.get(temposIndex)[1] <= event.getTick())
				{
					tempo = tempos.get(temposIndex)[0];
					temposIndex++;
				}
				
				// add the MIDI note events to the track (must have message length 3, message status 144, and message note between 21 and 108)
				if (event.getMessage().getLength() == 3 && event.getMessage().getStatus() == 144
					&& event.getMessage().getMessage()[1]-12 >= 21 && event.getMessage().getMessage()[1]-12 <= 108)
				{
					
					tickAdjustment = tempo / 500000.0;   // calculate tick adjustment factor relative to 120 bpm
					
					tick = (long)((event.getTick() - prevTick) * tickAdjustment) + prevTickMoved;
					
					if (shift < 0)   // set shift if it has not been set
						shift = tick;
					
					newTrack.add(new MidiEvent(event.getMessage(), tick - shift));
					
					prevTick = event.getTick();
					prevTickMoved = tick;
				}
			}
			
			return newTrack;
		}
		else
		{
			ArrayList<Track> front = new ArrayList<Track>(tracks.subList(0, tracks.size()/2));   // contains the tracks from the first half of tracks
			ArrayList<Track> back = new ArrayList<Track>(tracks.subList(tracks.size()/2, tracks.size()));   // contains the tracks from the last half of tracks
			
			// newTracks is an array that contains two tracks, the sorted tracks for front and back
			ArrayList<Track> newTracks = new ArrayList<Track>();
			newTracks.add(sortedTrack(front, sequence, tempos));
			newTracks.add(sortedTrack(back, sequence, tempos));
			
			// newTracks is sorted
			return sortedTrack(newTracks, sequence, tempos);
		}
	}

	/**
	 * This method returns a new track that is the same as the input track but with a different resolution. Instead of
	 * 480 ticks per quarter note, the returned track has a resolution of 48 ticks per quarter note, which is accomplished
	 * by dividing the tick value of every event in track by 10.
	 * <br>
	 * PRECONDITION:	track has a resolution of 480 ticks per quarter notes
	 * 
	 * @param track			the track to change the resolution of
	 * @param sequence		the sequence in which the returned track is to be created
	 * @return a track with an adjusted resolution
	 */
	public static Track changeRes(Track track, Sequence sequence)
	{
		Track newTrack = sequence.createTrack();
		
		// add each event in track to newTrack, but divide the tick by 10
		for (int i = 0; i < track.size(); i++)
		{
			MidiEvent event = track.get(i);
			newTrack.add(new MidiEvent(event.getMessage(), event.getTick()/10));
		}
		
		return newTrack;
	}
	
	/**
	 * This method returns the track within a sequence that contains all of the MIDI tempo events, which are meta messages (a MIDI
	 * message that has a status byte of 0xFF, or 255). Tempo meta messages have a type byte 0x51, or 81. All the tracks in sequence
	 * are looped through and as soon as a tempo event is found, that track is returned.
	 * <br>
	 * PRECONDITION:	there is only one track within the sequence that contains MIDI tempo events
	 * 
	 * @param sequence		the sequence that is being checked
	 * @return the track within sequence that contains MIDI tempo events
	 */
	public static Track tempoTrack(Sequence sequence)
	{
		Track[] tracks = sequence.getTracks();
		
		for (Track track : tracks)
			for (int i = 0; i < track.size(); i++)
				if (track.get(i).getMessage().getStatus() == 255 && ((MetaMessage)track.get(i).getMessage()).getType() == 81)
					return track;
		
		return null;
	}
	
	/**
	 * This method generates a list of tempos (in microseconds per quarter notes), and the tick at which they occur. The method
	 * loops through all the MIDI events in track, and if the event is found to be a meta tempo event, the tempo is extracted.
	 * The tempo is stored within the meta message as a series of 3 bytes, which is converted into an integer using the wrap() function
	 * of the ByteBuffer class.
	 * 
	 * @param track		the track containing all the MIDI tempo events
	 * @return an ArrayList storing int arrays of length 2, holding the tempo and the tick at which this tempo is set
	 */
	public static ArrayList<int[]> tempos(Track track)
	{	
		ArrayList<int[]> temposList = new ArrayList<int[]>();
		int index = 0;
		
		// iterate through all events in track
		for (int i = 0; i < track.size(); i++)
		{
			MidiEvent event = track.get(i);
			MidiMessage message = event.getMessage();
			
			if (message.getStatus() == 255)   // tempo events have a message status of 255
			{
				MetaMessage meta = (MetaMessage)event.getMessage();
				
				if (meta.getType() == 81)   // tempo events have a meta message type of 81
				{
					// bytes is equal to the data from the tempo meta message
					byte[] bytes = {0, (byte)(meta.getData()[0]), (byte)(meta.getData()[1]), (byte)(meta.getData()[2])};
					ByteBuffer bb = ByteBuffer.wrap(bytes);
					int tempo = bb.getInt();   // convert bytes to an int through ByteBuffer
					tempo = (int)(Math.round(tempo/10000.0) * 10000);  // rounds tempo to the nearest ten thousand
					
					// unless the tempo would be the first tempo in the list, the tempo is only added if it is different from the previous tempo
					if (temposList.size() < 1 || tempo != temposList.get(index-1)[0])
					{
						int[] element = new int[2];
						element[0] = tempo;
						element[1] = (int)event.getTick();
						temposList.add(element);
						
						index++;
					}
				}
			}
		}
		
		return temposList;
	}
	
	/**
	 * This method takes a text file in the format as created by the textFile() method and outputs a MIDI file, with the same name
	 * but a different extension. The text in the text file is read using a BufferedReader and is examined character by character.
	 * MIDI events are added to the track of the MIDI file sequence whenever a note is found. The title of the MIDI file is also set,
	 * the instrument is set to piano, and the end of the track is indicated.
	 * 
	 * @param textFile		the text file to be converted into a MIDI file
	 * @param folder		the parent folder of the returned file
	 * @return a MIDI file converted from a text file
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws InvalidMidiDataException
	 */
	public File midiFile(File textFile, File folder) throws IOException, FileNotFoundException, InvalidMidiDataException
	{
		// create a new file in folder with the same same as textFile but with a MIDI extension
		String textFileName = textFile.getName();
		File midiFile = new File(folder, textFileName.substring(0, textFileName.indexOf(".")) + ".mid");
		
		BufferedReader br = new BufferedReader(new FileReader(textFile));   // create a BufferedReader for the file textFile
		String text = validString(br.readLine());   // create a valid String storing the text in textFile  
		
		// create new sequence and track within the sequence
		Sequence sequence = new Sequence(Sequence.PPQ, 48);
		Track track = sequence.createTrack();
		
		long tick = 0;
		char ch;
		
		// iterate for each character in text
		for (int i = 0; i < text.length(); i++)
		{
			ch = text.charAt(i);
			
			// if ch is a space, increment tick
			if (ch == ' ')
			{
				tick++;
			}
			// if ch is not an exclamation mark or tilde, ch represents a note
			else if (ch != '!' && ch != '~')
			{
				int note = ch - 13;
				String lengthStr = text.substring(i+1, i+4);   // get the next 3 characters (numbers)
				int length = Integer.parseInt(lengthStr);   // parse to get int
				
				// add note on
				ShortMessage smOn = new ShortMessage();
				smOn.setMessage(144, note, 50);
				MidiEvent eventOn = new MidiEvent(smOn, tick);
				track.add(eventOn);
				
				// add note off
				ShortMessage smOff = new ShortMessage();
				smOff.setMessage(144, note, 0);
				MidiEvent eventOff = new MidiEvent(smOff, tick + length);
				track.add(eventOff);
				
				i += 3;   // increment i by 3 (to ignore length of note)
			}
		}
		
		// set track name
		MetaMessage mm = new MetaMessage();
		String trackName = textFileName.substring(0, textFileName.indexOf("."));
		mm.setMessage(3, trackName.getBytes(), trackName.length());
		MidiEvent me = new MidiEvent(mm, 0);
		track.add(me);
		
		// set instrument to piano
		ShortMessage sm = new ShortMessage();
		sm.setMessage(192, 0, 0);
		me = new MidiEvent(sm, 0);
		track.add(me);
		
		// set end of track
		mm = new MetaMessage();
		byte[] bt = {};
		mm.setMessage(47, bt, 0);
		me = new MidiEvent(mm, tick);
		track.add(me);
		
		// write the sequence to midiFile
		MidiSystem.write(sequence, 1, midiFile);
		
		br.close();
		
		return midiFile;
	}
	
	/**
	 * This method takes in the String of notes generated by the computer as an input, and modifies the String (if needed) so that the
	 * notation is correct. All invalid characters or series or characters are removed from the String.
	 * 
	 * @param str	the String containing all the text extracted from the text file (that is to be converted into a MIDI file)
	 * @return str with all invalid characters removed
	 */
	public static String validString(String str)
	{
		String newStr = "";
		
		char ch;
		int expect = -1;   // -1 for anything, 0 for space or exclamation mark, 1 for space, 2 for tilde or note, 3 for note
		int result;   // -1 for nothing, 0 for space, 1 for exclamation mark, 2 for tilde, 3 for note
		
		// iterate through all the characters in str
		for (int i = 0; i < str.length(); i++)
		{
			ch = str.charAt(i);
			String addStr = "" + ch;   // set addStr to current character
			
			// set the result based on the character read
			// if a character that is not ' ', '!', or '~' is found, the next 3 characters are checked to see if they are digits (to see if a valid note has been found)
			// otherwise, the result is set to nothing (-1)
			if (ch == ' ')
			{
				result = 0;
			}
			else if (ch == '!')
			{
				result = 1;
			}
			else if (ch == '~')
			{
				result = 2;
			}
			else if (i + 3 < str.length() && ch >= 34 && ch <= 121 && Character.isDigit(str.charAt(i+1))
					   && Character.isDigit(str.charAt(i+2)) && Character.isDigit(str.charAt(i+3)))
			{
				result = 3;
				addStr += "" + str.charAt(i+1) + str.charAt(i+2) + str.charAt(i+3);   // add entire note to addStr
				i += 3;   // skip over next 3 characters
			}
			else
			{
				result = -1;
			}
			
			// add the character or series of characters if they line up with what was expected
			if (expect == -1 && result != -1 ||
				 expect == 0 && (result == 0 || result == 1) ||
				 expect == 1 && result == 0 ||
				 expect == 2 &&  (result == 2 || result == 3) ||
				 expect == 3 && result == 3)
			{
				newStr += addStr;
			}
			
			// change the expectation, based on the result
			if (result == 0)   // if the result was a space, a note or tilde is expected
				expect = 2;
			else if (result == 1)   // if the result was a tilde, a note is expected
				expect = 3;
			else if (result == 2)   // if the result was a tilde, a space is expected
				expect = 1;
			else if (result == 3)   // if the result was a note, a space or exclamation mark is expected
				expect = 0;
			else   // if the result was nothing, anything is expected
				expect = -1;
		}
		
		return newStr;
	}
	
}
