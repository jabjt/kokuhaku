package com.kokuhaku.client;

/**
 * IMA ADPCM: 4 bits a sample, a quarter of the size. The encoder carries its
 * state from one frame to the next, and each frame's header says where it had
 * got to, so a lost frame costs only that frame. Starting each frame from
 * scratch would make every one begin badly, 25 times a second.
 */
public final class Adpcm {
    private static final int[] STEPS = {7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
            107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282,
            1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487,
            12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
    private static final int[] INDEX_STEP = {-1, -1, -1, -1, 2, 4, 6, 8};

    private int predicted = 0, index = 0;

    public byte[] encode(short[] samples) {
        byte[] out = new byte[3 + samples.length / 2];
        out[0] = (byte) predicted;
        out[1] = (byte) (predicted >> 8);
        out[2] = (byte) index;
        for (int i = 0; i < samples.length; i++) {
            int step = STEPS[index], diff = samples[i] - predicted, code = 0;
            if (diff < 0) {
                code = 8;
                diff = -diff;
            }
            if (diff >= step) {
                code |= 4;
                diff -= step;
            }
            if (diff >= step / 2) {
                code |= 2;
                diff -= step / 2;
            }
            if (diff >= step / 4) code |= 1;
            predicted = next(predicted, step, code);
            index = clamp(index + INDEX_STEP[code & 7]);
            out[3 + i / 2] |= (byte) (i % 2 == 0 ? code : code << 4);
        }
        return out;
    }

    /** Back to silence, for the start of the next thing you say. */
    public void reset() {
        predicted = 0;
        index = 0;
    }

    public static short[] decode(byte[] in) {
        int count = (in.length - 3) * 2;
        short[] samples = new short[count];
        int predicted = (short) ((in[1] << 8) | (in[0] & 0xFF)), index = clamp(in[2]);
        for (int i = 0; i < count; i++) {
            int code = (in[3 + i / 2] >> (i % 2 == 0 ? 0 : 4)) & 15;
            predicted = next(predicted, STEPS[index], code);
            index = clamp(index + INDEX_STEP[code & 7]);
            samples[i] = (short) predicted;
        }
        return samples;
    }

    private static int next(int predicted, int step, int code) {
        int delta = step >> 3;
        if ((code & 4) != 0) delta += step;
        if ((code & 2) != 0) delta += step >> 1;
        if ((code & 1) != 0) delta += step >> 2;
        int result = (code & 8) != 0 ? predicted - delta : predicted + delta;
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, result));
    }

    private static int clamp(int index) {
        return Math.max(0, Math.min(STEPS.length - 1, index));
    }
}
