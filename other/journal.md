# Journal

I decided to split this project into two separate components, first choosing to focus on generating the text files that would be run through the machine learning program, and then creating the machine learning program itself. I chose to develop the neural network second, so that I would be able to test it out immediately with the data that I had converted.


### Week 1: File Converter

Over the course of 6 to 7 days, I developed the program to convert the MIDI files into a text format that is able to be interpreted by the neural network. Within my Java project, I created a FileConverter class that serves the purpose of performing this task. Within this class, I first outlined the method that would be called from the driver classes, called textFile(). In the process of developing this method, I found that I needed to add many additional static methods along the way. These helper methods and the textFile() method were tested by generating text files that displayed the data in the format `| (note #) (velocity) (tick) |`. For example:

```
26 57 0|33 57 0|38 69 0|54 60 0|57 60 0|62 73 0|26 0 40|33 0 40|38 0 40|54 0 40|57 0 40|62 0 40|
```

using:

```java
for (int i = 0; i < newTrack.size(); i++)
{
    MidiEvent event = newTrack.get(i);
    MidiMessage message = event.getMessage();
    
    if (message.getLength() == 3 && message.getStatus() == 144)
    {
        byte[] data = message.getMessage();
            
        fw.write(data[1]-12 + " " + data[2] + " " + event.getTick() + " | ");
    }
}
```

This format served to be very useful in debugging my program, since I was able to check if the tracks had been correctly merged according to tick, if the notes were in ascending order for each tick, if the tick for each note was adjusted correctly according to the tempo at that point, and if the resolution of the sequence had been changed by the correct factor. This format was used throughout the development of the helper methods, and it wasn't until near the end that I began to write the files in the final notation.

After I finished the textFile() method and all of its extensions, I wrote another method within the FileConverter class to convert the text files back into MIDI files. This method, called midiFile(), only has one helper method, and for that reason, it took me about half the time to write this method compared to the textFile() method. This method is less complex since it simply extracts all the notes from a text file, along with their length and tick, and adds them as MIDI events to a singular track, which is created within the sequence that is written to the MIDI file.

I ultimately tested both the textFile() and midiFile() methods by converting the text files created in "txt-training" back into MIDI files (placed in a folder called "midi-files" which can now be found in the "other" folder). This was done with:

```java
package composer;
import java.io.File;
import java.io.IOException;
import javax.sound.midi.InvalidMidiDataException;

public class MidiFileCreator
{
	
	public static void main(String[] args) throws IOException, InvalidMidiDataException
	{
		FileConverter fileConverter = new FileConverter();
		
		File midi = new File("midi-files");
		midi.mkdir();
		
		File text = new File("txt-training");
		File[] textFiles = text.listFiles();
		
		for (File textFile : textFiles)
		{
			File midiFile = fileConverter.midiFile(textFile, midi);
			midiFile.createNewFile();
		}
	}
	
}
```

Surpisingly, I was able to open the MIDI files in GarageBand with no initial signs of error. The first file I played was the First Movement of Mozart's Sonata in D Major K. 311, which converted extremely well. As expected, the quality of the music did decrease, and sounded more artificial since I had chosen to ignore all events that allow for more fluid, human-like sound, for example, pedal, and had made all note on events the same velocity (50). However, when I tried the Second Movement, the song played way too fast--to the point where it sounded like 5 bars were being played at the same time. My first assumption was that there was something wrong with the tempo conversion, and this ended up being the case. In the tempos() method, I had used bitwise operations to complete the task of converting an array of 3 bytes into an integer. This ended up causing problems--which I discovered by converting different tempos into byte arrays and vice versa--because these operations are only supposed to be used when the bytes are unsigned, and Java uses signed bytes. I was able to solve this problem by instead using the ByteBuffer class of the Java NIO package. This class has methods to convert byte arrays into integers, and solved the problem of certain songs playing too fast. However, once I had done this, the tempo of the Second Movement became slower than it should be. I did not think that new difference in tempo was not too much of a major problem, which was good considering that I was not able to figure out why only one file out of the 21 training files experienced this error. Currently, I attribute the bug to the result of rounding the tempos too much, but I don't know if this is true.

Overall, this step ended up taking longer than expected, because I had previously never worked with MIDI files and was navigating the Java MIDI API for the first time. This increased the length of time it took to write the file converter because it meant that I often had to consult Oracle’s [MIDI API documentation](https://docs.oracle.com/javase/7/docs/api/javax/sound/midi/package-summary.html), among other resources. 

In addition, another challenge that I encountered was that I found it hard to develop a notation for the text files that would store enough information about the song which would allow the text file to be converted into a MIDI file, while also keeping the text file somewhat compact and easily readable by the neural network. It took some experimentation to find the correct balance between the MIDI file resolution and the size of the resultant text files. This is because the size of the text files increased as the resolution increased, however the resolution couldn't be too low because this would mean that some of the ornamentation within the music would be sacrificed.


### Week 2: Neural Network

After the text training files had been created, I began to work on developing the neural network model that would be used to generate the new text files. The file generator program did not take as long to develop compared to the file converter since much of the methods that were necessary to build the neural network had already been programmed, and just needed to be accessed from the Tensorflow Python package.

At first, I started to code the machine learning program in Java, developing the program based on the conceptual information and the mathematics behind recurrent neural networks that I could find. However, I realized that this would be overly complicated since neural network machine learning often involves large vector operations and multivariable calculus. I also thought that creating my own neural network would be too much unneccessary work, since there are already numerous open source libraries that can build neural networks, that would be much more efficient and effective then what I could have programmed in a week. I found that it would be better to not use Java for this task, since Python would be better suited because of the well-developed machine learning libraries available, like Tensorflow. Although there are some libraries for Java, I found that there was not as much documentation and tutorials as for Python, which are both things that would be important in helping me finish the project in a shorter amount of time.

In total, it took me only about 2 classes to complete this component since I pretty much followed a very descriptive Tensorflow tutorial line for line. The only things I had to change were the location of the training data and the size of the final sequence of text generated (I increased the number of characters to make the file convert to a song of typical length). I also added the code at the start to merge all the text files in "txt-training" into one large text file, called "txt_file.txt."


### Week 2.5: Training & Implementation

The file generator was then run to train the model for 3 "epochs," which took around 24 hours of CPU time when being run on the 2.7 GHz Intel Core i5 processor of a Macbook Pro. This spread the training over nearly 3 days, since IDLE would not run when my laptop was sleeping, which would be for a large portion of the day. To make up for this lost time, I kept my laptop on over night to allow for the model to continue training. During the training process, it was unfortunate because the estimated time left always seemed to be incredibly off--for example, if it said there was 1 hour remaining, this would be equalivalent to 5 more hours of training. In addition, IDLE drained my laptop's battery very quickly, and used up most of my CPU (always more than 99.5%).

As can be seen from the training log, the loss began to plateau during the third epoch (remaining at a value of around 0.33). If I trained the model for more time, I would have liked to see if this training would have had any effect on the quality of the files produced. I would also have further experimented with the file_generator program to figure out what sequence size, batch size, etc. would optimize the effectiveness of the model. 

For some reason, I was not able to write the text the file generator printed to a file, so I ended up just printing the characters and manually saving it to a text file. I then placed these files into a folder called "txt-files," and used the MidiFileCreator class to convert the files in this folder into MIDI files. I only had the computer generate 3 text files--there would be no point in creating more since they would be equally as bad. The computer actually did a good job in making the format of the files correct for the most part. There were a few errors, such as random characters appearing in random places and the computer was never able to start a file off correctly with a note, which is why I wrote the validString() method within the FileConverter class to make sure that only valid notes are added to the MIDI file. Without this method, the text files would not be able to be converted into MIDI files since the midiFile() method would throw many errors.

Finally, I converted the new MIDI files into MP3 files. This was done so that the files could be listened to from devices without MIDI readers.
