## How It Works

### Encoding
1. Audio is mu-law companded with mu = 63
2. Then the companded value is quantised to signed 6 bits
3. Said process is done to *every other* audio sample

### Decoding
1. A sample is reverse mu-law companded
2. The quantised value is expanded to signed 16 bits
3. The recovered sample is duplicated once to match the original sampling rate (remember: only every other audio sample has been encoded)
4. During the process of step 3, linear interpolation is applied to smooth out the stairstep and make the resulting audio tad more pleasant to listen

Encoded audio's bitrate is **96 kbps** for the sampling rate of **32 000 Hz**. Of course, you can get near-CD quality audio with much more advanced codecs such as Opus, but this crappy compander has one very subtle advantage that it will produce proudly garbage-like garbage (and absolutely won't error out) when garbage (say, a text file containing long-winded BASIC code) is fed, an advantage that almost nobody would benefit from.