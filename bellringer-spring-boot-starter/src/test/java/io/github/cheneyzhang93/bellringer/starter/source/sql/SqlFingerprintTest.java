package io.github.cheneyzhang93.bellringer.starter.source.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 指纹归一化验收：注释剥离、字面量替换、空白折叠、大小写统一、空值兜底。
 */
class SqlFingerprintTest {

    @Test
    void replacesNumberAndStringLiterals() {
        assertThat(SqlFingerprint.of("select * from orders where id = 123"))
                .isEqualTo("select * from orders where id = ?");
        assertThat(SqlFingerprint.of("select * from users where name = 'zhangsan'"))
                .isEqualTo("select * from users where name = ?");
        assertThat(SqlFingerprint.of("select * from t where amount > 12.5"))
                .isEqualTo("select * from t where amount > ?");
    }

    @Test
    void keepsIdentifierDigitsGluedToWords() {
        assertThat(SqlFingerprint.of("select col1, col2 from t1 where t1.id = 9"))
                .isEqualTo("select col1, col2 from t1 where t1.id = ?");
    }

    @Test
    void stripsLineAndBlockComments() {
        assertThat(SqlFingerprint.of("select 1 -- 只取常量\nfrom dual"))
                .isEqualTo("select ? from dual");
        assertThat(SqlFingerprint.of("select /* hint */ 1 from dual"))
                .isEqualTo("select ? from dual");
    }

    @Test
    void doesNotTreatCommentMarkerInsideLiteralAsComment() {
        assertThat(SqlFingerprint.of("select * from t where note = 'a -- b'"))
                .isEqualTo("select * from t where note = ?");
    }

    @Test
    void collapsesWhitespaceAndLowerCase() {
        assertThat(SqlFingerprint.of("  SELECT   *\n  FROM\tT  WHERE ID = 7 "))
                .isEqualTo("select * from t where id = ?");
    }

    @Test
    void blankFallsBackToUnknownSql() {
        assertThat(SqlFingerprint.of(null)).isEqualTo("unknown-sql");
        assertThat(SqlFingerprint.of("   ")).isEqualTo("unknown-sql");
    }
}
