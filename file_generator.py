from __future__ import absolute_import, division, print_function

import tensorflow as tf
tf.enable_eager_execution()

import numpy as np
import os
import glob
import time


# READ AND PROCESS THE TEXT IN THE TEXT FILES

# combine text files in txt-training into one text file called txt-file.txt
with open('txt_file.txt', 'w') as outfile:
    for file in  glob.glob(os.path.join('txt-training', '*.txt')):
        with open(file) as infile:
            outfile.write(infile.read())

path_to_file = 'txt_file.txt'

# read and decode text file
text = open(path_to_file, 'rb').read().decode(encoding='utf-8')

# get the unique characters in the file
vocab = sorted(set(text))

# create a mapping from unique characters to indices, and vice versa
char2idx = {u:i for i, u in enumerate(vocab)}
idx2char = np.array(vocab)

# text as a series of integers, according to the maps
text_as_int = np.array([char2idx[c] for c in text])


# CREATE TRAINING EXAMPLES AND TARGETS

seq_length = 100   # the number of characters for each training example
examples_per_epoch = len(text)//seq_length

# convert the text vector into a stream of character indices
char_dataset = tf.data.Dataset.from_tensor_slices(text_as_int)

# convert the individual characters to sequences of the desired size
sequences = char_dataset.batch(seq_length+1, drop_remainder=True)

# duplicate and shift each sequence to form the input and target text
def split_input_target(chunk):
    input_text = chunk[:-1]
    target_text = chunk[1:]
    return input_text, target_text

dataset = sequences.map(split_input_target)

BATCH_SIZE = 64   # batch size
steps_per_epoch = examples_per_epoch//BATCH_SIZE

BUFFER_SIZE = 10000   # buffer size to shuffle the dataset

# shuffle the data and organize the data into batches
dataset = dataset.shuffle(BUFFER_SIZE).batch(BATCH_SIZE, drop_remainder=True)

vocab_size = len(vocab)   # length of the vocabulary in chars

embedding_dim = 256   # embedding dimension

rnn_units = 1024   # number of RNN units


# BUILD THE MODEL --- 3 layers

# use CuDNNGRU if running on GPU
if tf.test.is_gpu_available():
    rnn = tf.keras.layers.CuDNNGRU
else:
    import functools
    rnn = functools.partial(tf.keras.layers.GRU, recurrent_activation='sigmoid')

def build_model(vocab_size, embedding_dim, rnn_units, batch_size):
    model = tf.keras.Sequential([
        tf.keras.layers.Embedding(vocab_size, embedding_dim,
                                  batch_input_shape=[batch_size, None]),
        rnn(rnn_units,
            return_sequences=True,
            recurrent_initializer='glorot_uniform',
            stateful=True),
        tf.keras.layers.Dense(vocab_size)
    ])
    return model

model = build_model(vocab_size = len(vocab),
                    embedding_dim=embedding_dim,
                    rnn_units=rnn_units,
                    batch_size=BATCH_SIZE)


# TRAIN THE MODEL

# attach a loss function
def loss(labels, logits):
    return tf.keras.losses.sparse_categorical_crossentropy(labels, logits,
                                                           from_logits=True)

# attach an optimizer
model.compile(optimizer = tf.train.AdamOptimizer(), loss = loss)

# directory where the checkpoints will be saved
checkpoint_dir = 'training_checkpoints'
# name of the checkpoint files
checkpoint_prefix = os.path.join(checkpoint_dir, 'ckpt_{epoch}')

# ensure that checkpoints are saved during training
checkpoint_callback = tf.keras.callbacks.ModelCheckpoint(filepath=checkpoint_prefix,
                                                       save_weights_only=True)
EPOCHS = 15   # number of epochs to train the model

# train the model
history = model.fit(dataset.repeat(), epochs=EPOCHS,
                    steps_per_epoch=steps_per_epoch,
                    callbacks=[checkpoint_callback])


# GENERATE TEXT

# restore the weights from the latest checkpoint
tf.train.latest_checkpoint(checkpoint_dir)

# rebuild model with batch size of 1
model = build_model(vocab_size, embedding_dim, rnn_units, batch_size=1)
model.load_weights(tf.train.latest_checkpoint(checkpoint_dir))
model.build(tf.TensorShape([1, None]))

def generate_text(model, start_string):
  # Evaluation step (generating text using the learned model)
  
  num_generate = 50000   # number of characters to generate

  # convert start string to numbers (vectorize) 
  input_eval = [char2idx[s] for s in start_string]
  input_eval = tf.expand_dims(input_eval, 0)

  # empty string to store results
  text_generated = []

  # low temperatures result in more predictable text
  # higher temperatures results in more surprising text
  temperature = 1.0

  # here batch size == 1
  model.reset_states()
  for i in range(num_generate):
      predictions = model(input_eval)
      # remove the batch dimension
      predictions = tf.squeeze(predictions, 0)

      # use a multinomial distribution to predict the word returned by the model
      predictions = predictions / temperature
      predicted_id = tf.multinomial(predictions, num_samples=1)[-1,0].numpy()
      
      # pass the predicted word as the next input to the model, along with the previous hidden state
      input_eval = tf.expand_dims([predicted_id], 0)
      
      text_generated.append(idx2char[predicted_id])

  return (start_string + ''.join(text_generated))

# print generated text
generated_text = generate_text(model, start_string=u"'")
print(generated_text)

