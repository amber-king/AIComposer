# AIComposer

In this project, I attempted to develop a program to allow a computer to generate classical music using machine learning. The machine learning model that was used was a recurrent neural network (RNN) that was obtained from the Tensorflow Python package. This model was "trained" for around 24 hours (using a CPU) on over 2 hours of Mozart sonatas. It was hoped that the computer would eventually be able to create its own music that would resemble a Mozart sonata, however, this was not accomplished, as shown by the three trash music files in the "mp3-files" folder that you can listen to if you want.


## FileConverter.java

In this project, I trained the model using text files that had been generated from MIDI files. A MIDI file is a Musical Instrument Digital Interface file that stores 

which is what is used by MIDI files to measure time. For these files, there are 48 MIDI ticks for each quarter note (this is the resolution), and each quarter note is 500,000 microseconds long, which is equivalent to 120 beats per minute

The Mozart MIDI files used for training the model were sequenced by Bernd Krueger, and were retrieved from http://www.piano-midi.de/. These files were placed in a folder called "midi-training" which the program "TextFileCreator.java" loops through. The TestFileCreator class first creates a new folder called "txt-training," and then uses a FileConverter object to convert all the MIDI files in "midi-training" into new text files in "txt-training." If you take a look at one of the training text files, they all look something like this: 

'''
'040!.040!3040!C040!F040!K040 ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ 
'''

Each space seperates the MIDI ticks and each exclamation point seperates the different notes if several are being played at the same time. Each note is stored in 4 different characters: the first is the note value represented by an ASCII character and the last 3 represent a padded 3-digit integer for the length of the note (in MIDI ticks). The ASCII character is in the range 34 to 121, characters '"' to 'y' on the ASCII table (this range was chosen so that it would include no non-printing characters, and also " ", "!", "~" would be left out). This is because there are 88 notes on a piano keyboard, represented by MIDI numbers 21 to 108. So the character can simply be found by adding 13 to the MIDI number. If there are no notes initialized on a tick, then the tick is represented by a tilde, which is why the text files seem to be mainly composed of these characters.

The sequence of notes above actually represents the first chord of Mozart's Sonata in D Major, K. 311. Obtaining the notes for the characters ''', '.', '3', 'C', 'F', and 'K' gives us 26, 33, 38, 54, 57, and 62, respectively, which are notes that form a D major chord.

![ASCII Characters](http://www.asciitable.com/index/asciifull.gif)

![MIDI Notes](https://newt.phys.unsw.edu.au/jw/graphics/notes.GIF)

The MIDI files were converted into text files of the specified format through the textFile() method within the FileConverter class. This class implemented the [Java MIDI API](https://docs.oracle.com/javase/7/docs/api/javax/sound/midi/package-summary.html) in order extract the necessary information from the MIDI files. The MIDI file was first converted into a sequence, with several tracks. Each Track object holds many MidiEvent objects, which contain a MidiMessage and the tick at which the event occurs. Each MidiMessage has a status byte that indicates the type of MidiMessage, for example, note on, note off, or meta event.

For this project, the only MIDI events I needed to worry about were note on events, and tempo meta events. In all the MIDI files used for training, there were no note off events, and instead, the end of the notes were indicated with later events containing the same note but with a velocity of 0. 



This textFile() method also implemented many static helper methods, including: noteFound(), sortedTrack(), changeRes(), tempoTrack(), and tempos(). Full descriptions of these helper methods can be found in the Javadoc documentation within the class.

The text files were converted into MIDI files using the midiFile() method. The text in the text file was first extracted and stored as a String using the FileReader class. The String was examined character by character, with each character following a space or exclamation mark being stored as a note with the length obtained by parsing the three successive numerical characters. For each note, a MIDI message was created that held the byte value of the note, with velocity 50. A MIDI event was added to a track containing the MIDI message and the tick, which was the current tally of the number of spaces. Another message was created to represent the note off, which has velocity 0. Another event for this message was added to the track, this time the tick being the tick tally plus the length of the note. The track containing the MIDI events was created as a part of a sequence which was written to a new file using the MidiSystem.write() function. Within the track, a  

No tempo meta messages needed to be written, since the default tempo for a MIDI file is 120 bpm, which is what was wanted.


## file_generator.py

Once the MIDI files for the classical music had been converted into text files, the text files were passed through a machine learning program that generated brand new text files based on patterns observed in the training data. In this project, I used a recurrent neural network (RNN) to generate the files, since these models are able to recognize patterns in sequences in data and are able to generate new sequences by making predictions based on previous inputs. These features make RNNs especially useful in developing programs for speech recognition, language modeling, translation, image captioning, etc.. RNNs have been used to generate text since after training, they can learn to predict the character that will next, based on the preceding character, and the characters before that. This is how an RNN was used to generate music, generating text files letter by letter, unlike how human composers would write music, which would be by choosing sequences of notes based on their flow and audible resonation. It was hoped that the computer would eventually learn the patterns of the text files and be able to write notationally correct files, and ultimately, would be able to generate text files that could be converted into decent sounding music. 

Initially, I planned to develop my own simple recurrent neural network in Java, however, I found that it would be a better idea to develop a model using open source machine learning resources already available. As a result, I ended up programming the "AI" aspect of this project in Python, since there are many well-developed machine learning libraries available, compared to for Java. I ultimately chose to use Tensorflow, which is frequently used to build RNNs and LSTMs. To write the file_generator program, I followed a [tutorial](https://www.tensorflow.org/tutorials/sequences/text_generation) by Tensorflow that provided step by step instructions on how to write a text generator using RNNs.

In the Python program, the text files in txt-training were first merged into one text file called “txt-file.txt.” The text in this file was extracted, to form one large string of training data. 

To solve the problem of errors during the conversion of the new text files into MIDI files, I made the program only add  

 

If I had more time, I would have further experimented with this code to figure out what sequence size, batch size, etc. would optimize the effectiveness of the model. 
