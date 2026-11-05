package com.kokuhaku.game;

import com.kokuhaku.game.Traits.Glasses;
import com.kokuhaku.game.Traits.Hair;
import com.kokuhaku.game.Traits.Homeroom;
import com.kokuhaku.game.Traits.Length;

import java.util.ArrayList;
import java.util.List;

/**
 * Everyone who can be at the school. Ids 0-25 are the girls, 26-51 the boys.
 * A run uses 20 of each, plus 3 of each per extra player. Looks and class are
 * fixed; clubs and where they live are shuffled every run (see Mystery).
 */
public final class Roster {
    public enum Personality { CHEERFUL, SHY, COLD, GOSSIP, POLITE, SPORTY }

    public record Student(int id, String name, boolean female, Hair hair, Length length, Glasses glasses,
                          Homeroom homeroom, Personality personality) {
        public String firstName() {
            return name.substring(0, name.indexOf(' '));
        }
    }

    public static final int GIRLS = 26;
    public static final int TOTAL = 52;
    public static final int MAX_PARTY = 3;

    private static final Hair BK = Hair.BLACK, BR = Hair.BROWN, BL = Hair.BLONDE, RD = Hair.RED, PK = Hair.PINK, SV = Hair.SILVER;
    private static final Length S = Length.SHORT, L = Length.LONG;
    private static final Glasses G = Glasses.YES, N = Glasses.NO;
    private static final Homeroom A = Homeroom.A, B = Homeroom.B;
    private static final Personality CH = Personality.CHEERFUL, SH = Personality.SHY, CO = Personality.COLD,
            GO = Personality.GOSSIP, PO = Personality.POLITE, SP = Personality.SPORTY;

    public static final List<Student> STUDENTS = List.of(
            girl(0, "Aoi Tanaka", BK, L, N, A, PO),
            girl(1, "Hana Suzuki", BR, S, N, A, CH),
            girl(2, "Yui Takahashi", BL, L, G, B, GO),
            girl(3, "Sakura Ito", PK, L, N, A, SH),
            girl(4, "Rin Watanabe", BK, S, G, B, CO),
            girl(5, "Mio Yamamoto", RD, L, N, B, SP),
            girl(6, "Akari Nakamura", BR, L, G, A, SH),
            girl(7, "Hinata Kobayashi", BL, S, N, A, SP),
            girl(8, "Mei Kato", SV, L, N, B, CO),
            girl(9, "Koharu Yoshida", PK, S, G, B, CH),
            girl(10, "Nanami Yamada", BK, L, G, A, PO),
            girl(11, "Ayaka Sasaki", BR, S, G, B, GO),
            girl(12, "Riko Matsumoto", RD, S, N, A, CH),
            girl(13, "Yuna Inoue", BK, L, N, B, SH),
            girl(14, "Chika Kimura", PK, L, G, A, GO),
            girl(15, "Emi Hayashi", BR, L, N, B, PO),
            girl(16, "Kaede Shimizu", SV, S, G, A, CO),
            girl(17, "Shiori Mori", BL, L, N, B, SH),
            girl(18, "Natsuki Ikeda", BR, S, N, A, SP),
            girl(19, "Tsubaki Ogawa", BK, S, N, B, CO),
            girl(20, "Ruka Hashimoto", SV, L, G, A, SH),
            girl(21, "Noa Ishii", RD, L, G, B, PO),
            girl(22, "Honoka Abe", BL, S, G, A, CH),
            girl(23, "Sayuri Kondo", BK, S, G, B, SH),
            girl(24, "Mana Saito", PK, S, N, A, GO),
            girl(25, "Kanon Nomura", SV, L, N, B, SP),

            boy(26, "Haruto Fujita", A, CH), boy(27, "Sota Okada", B, SP), boy(28, "Ren Goto", A, CO),
            boy(29, "Yuto Hasegawa", B, GO), boy(30, "Kaito Ishikawa", A, SP), boy(31, "Riku Maeda", B, SH),
            boy(32, "Daiki Murakami", A, PO), boy(33, "Kenji Sakamoto", B, GO), boy(34, "Takumi Endo", A, SH),
            boy(35, "Shota Aoki", B, CH), boy(36, "Hayato Fujii", A, CO), boy(37, "Yamato Nishimura", B, PO),
            boy(38, "Kenta Fukuda", A, GO), boy(39, "Ryo Ota", B, SP), boy(40, "Itsuki Miura", A, SH),
            boy(41, "Sho Okamoto", B, CH), boy(42, "Taiga Matsuda", A, SP), boy(43, "Kazuki Nakajima", B, CO),
            boy(44, "Naoki Harada", A, PO), boy(45, "Daichi Ono", B, GO),
            boy(46, "Jun Kubo", A, CH), boy(47, "Masato Arai", B, SH), boy(48, "Yusuke Wada", A, CO),
            boy(49, "Tomoya Ueda", B, PO), boy(50, "Akira Hirano", A, SP), boy(51, "Shun Kikuchi", B, GO)
    );

    private Roster() {
    }

    public static Student get(int id) {
        return STUDENTS.get(id);
    }

    public static int girlsFor(int party) {
        return 20 + 3 * (party - 1);
    }

    public static int boysFor(int party) {
        return girlsFor(party);
    }

    /** The ids of everyone at the school for a party of this size. */
    public static List<Integer> active(int party) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < girlsFor(party); i++) ids.add(i);
        for (int i = 0; i < boysFor(party); i++) ids.add(GIRLS + i);
        return ids;
    }

    private static Student girl(int id, String name, Hair hair, Length length, Glasses glasses, Homeroom room, Personality p) {
        return new Student(id, name, true, hair, length, glasses, room, p);
    }

    private static Student boy(int id, String name, Homeroom room, Personality p) {
        return new Student(id, name, false, Hair.BLACK, Length.SHORT, Glasses.NO, room, p);
    }
}
