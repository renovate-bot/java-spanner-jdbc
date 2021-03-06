/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spanner.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.ResultSets;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Type;
import com.google.cloud.spanner.Type.StructField;
import com.google.cloud.spanner.connection.Connection;
import com.google.rpc.Code;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdbcPreparedStatementTest {
  private String generateSqlWithParameters(int numberOfParams) {
    StringBuilder sql = new StringBuilder("INSERT INTO FOO (");
    boolean first = true;
    for (int i = 0; i < numberOfParams; i++) {
      if (first) {
        first = false;
      } else {
        sql.append(", ");
      }
      sql.append("COL").append(i);
    }
    sql.append(") VALUES (");
    first = true;
    for (int i = 0; i < numberOfParams; i++) {
      if (first) {
        first = false;
      } else {
        sql.append(", ");
      }
      sql.append("?");
    }
    sql.append(")");
    return sql.toString();
  }

  private JdbcConnection createMockConnection() throws SQLException {
    return createMockConnection(mock(Connection.class));
  }

  private JdbcConnection createMockConnection(Connection spanner) throws SQLException {
    JdbcConnection connection = mock(JdbcConnection.class);
    when(connection.getSpannerConnection()).thenReturn(spanner);
    when(connection.createBlob()).thenCallRealMethod();
    when(connection.createClob()).thenCallRealMethod();
    when(connection.createNClob()).thenCallRealMethod();
    when(connection.createArrayOf(anyString(), any(Object[].class))).thenCallRealMethod();

    return connection;
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testParameters() throws SQLException, MalformedURLException {
    final int numberOfParams = 48;
    String sql = generateSqlWithParameters(numberOfParams);

    JdbcConnection connection = createMockConnection();
    try (JdbcPreparedStatement ps = new JdbcPreparedStatement(connection, sql)) {
      ps.setArray(1, connection.createArrayOf("INT64", new Long[] {1L, 2L, 3L}));
      ps.setAsciiStream(2, new ByteArrayInputStream("TEST".getBytes()));
      ps.setAsciiStream(3, new ByteArrayInputStream("TEST".getBytes()), 4);
      ps.setAsciiStream(4, new ByteArrayInputStream("TEST".getBytes()), 4l);
      ps.setBinaryStream(6, new ByteArrayInputStream("TEST".getBytes()));
      ps.setBinaryStream(7, new ByteArrayInputStream("TEST".getBytes()), 4);
      ps.setBinaryStream(8, new ByteArrayInputStream("TEST".getBytes()), 4l);
      ps.setBlob(9, connection.createBlob());
      ps.setBlob(10, new ByteArrayInputStream("TEST".getBytes()));
      ps.setBlob(11, new ByteArrayInputStream("TEST".getBytes()), 4l);
      ps.setBoolean(12, Boolean.TRUE);
      ps.setByte(13, (byte) 1);
      ps.setBytes(14, "TEST".getBytes());
      ps.setCharacterStream(15, new StringReader("TEST"));
      ps.setCharacterStream(16, new StringReader("TEST"), 4);
      ps.setCharacterStream(17, new StringReader("TEST"), 4l);
      ps.setClob(18, connection.createClob());
      ps.setClob(19, new StringReader("TEST"));
      ps.setClob(20, new StringReader("TEST"), 4l);
      ps.setDate(21, new Date(1000l));
      ps.setDate(22, new Date(1000l), Calendar.getInstance(TimeZone.getTimeZone("GMT")));
      ps.setDouble(23, 1d);
      ps.setFloat(24, 1f);
      ps.setInt(25, 1);
      ps.setLong(26, 1l);
      ps.setNCharacterStream(27, new StringReader("TEST"));
      ps.setNCharacterStream(28, new StringReader("TEST"), 4l);
      ps.setNClob(29, connection.createNClob());
      ps.setNClob(30, new StringReader("TEST"));
      ps.setNClob(31, new StringReader("TEST"), 4l);
      ps.setNString(32, "TEST");
      ps.setNull(33, Types.BIGINT);
      ps.setNull(34, Types.BIGINT, "INT64");
      ps.setObject(35, "TEST");
      ps.setObject(36, "TEST", Types.NVARCHAR);
      ps.setObject(37, "TEST", Types.NVARCHAR, 20);
      ps.setShort(40, (short) 1);
      ps.setString(42, "TEST");
      ps.setTime(43, new Time(1000l));
      ps.setTime(44, new Time(1000l), Calendar.getInstance(TimeZone.getTimeZone("GMT")));
      ps.setTimestamp(45, new Timestamp(1000l));
      ps.setTimestamp(46, new Timestamp(1000l), Calendar.getInstance(TimeZone.getTimeZone("GMT")));
      ps.setUnicodeStream(47, new ByteArrayInputStream("TEST".getBytes()), 4);
      ps.setURL(48, new URL("https://spanner.google.com"));
      ps.setObject(49, UUID.fromString("83b988cf-1f4e-428a-be3d-cc712621942e"));

      testSetUnsupportedTypes(ps);

      JdbcParameterMetaData pmd = ps.getParameterMetaData();
      assertEquals(numberOfParams, pmd.getParameterCount());
      assertEquals(JdbcArray.class.getName(), pmd.getParameterClassName(1));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(2));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(3));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(4));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(6));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(7));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(8));
      assertEquals(JdbcBlob.class.getName(), pmd.getParameterClassName(9));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(10));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(11));
      assertEquals(Boolean.class.getName(), pmd.getParameterClassName(12));
      assertEquals(Byte.class.getName(), pmd.getParameterClassName(13));
      assertEquals(byte[].class.getName(), pmd.getParameterClassName(14));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(15));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(16));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(17));
      assertEquals(JdbcClob.class.getName(), pmd.getParameterClassName(18));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(19));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(20));
      assertEquals(Date.class.getName(), pmd.getParameterClassName(21));
      assertEquals(Date.class.getName(), pmd.getParameterClassName(22));
      assertEquals(Double.class.getName(), pmd.getParameterClassName(23));
      assertEquals(Float.class.getName(), pmd.getParameterClassName(24));
      assertEquals(Integer.class.getName(), pmd.getParameterClassName(25));
      assertEquals(Long.class.getName(), pmd.getParameterClassName(26));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(27));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(28));
      assertEquals(JdbcClob.class.getName(), pmd.getParameterClassName(29));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(30));
      assertEquals(StringReader.class.getName(), pmd.getParameterClassName(31));
      assertEquals(String.class.getName(), pmd.getParameterClassName(32));
      assertEquals(Long.class.getName(), pmd.getParameterClassName(33));
      assertEquals(Long.class.getName(), pmd.getParameterClassName(34));
      assertEquals(String.class.getName(), pmd.getParameterClassName(35));
      assertEquals(String.class.getName(), pmd.getParameterClassName(36));
      assertEquals(String.class.getName(), pmd.getParameterClassName(37));
      assertNull(pmd.getParameterClassName(38));
      assertNull(pmd.getParameterClassName(39));
      assertEquals(Short.class.getName(), pmd.getParameterClassName(40));
      assertNull(pmd.getParameterClassName(41));
      assertEquals(String.class.getName(), pmd.getParameterClassName(42));
      assertEquals(Time.class.getName(), pmd.getParameterClassName(43));
      assertEquals(Time.class.getName(), pmd.getParameterClassName(44));
      assertEquals(Timestamp.class.getName(), pmd.getParameterClassName(45));
      assertEquals(Timestamp.class.getName(), pmd.getParameterClassName(46));
      assertEquals(ByteArrayInputStream.class.getName(), pmd.getParameterClassName(47));
      assertEquals(URL.class.getName(), pmd.getParameterClassName(48));
      assertEquals(UUID.class.getName(), pmd.getParameterClassName(49));

      ps.clearParameters();
      pmd = ps.getParameterMetaData();
      assertEquals(numberOfParams, pmd.getParameterCount());
    }
  }

  private void testSetUnsupportedTypes(PreparedStatement ps) {
    try {
      ps.setRef(38, (Ref) null);
      fail("missing expected exception");
    } catch (SQLException e) {
      assertTrue(e instanceof JdbcSqlException);
      assertEquals(Code.INVALID_ARGUMENT, ((JdbcSqlException) e).getCode());
    }
    try {
      ps.setRowId(39, (RowId) null);
      fail("missing expected exception");
    } catch (SQLException e) {
      assertTrue(e instanceof JdbcSqlException);
      assertEquals(Code.INVALID_ARGUMENT, ((JdbcSqlException) e).getCode());
    }
    try {
      ps.setSQLXML(41, (SQLXML) null);
      fail("missing expected exception");
    } catch (SQLException e) {
      assertTrue(e instanceof JdbcSqlException);
      assertEquals(Code.INVALID_ARGUMENT, ((JdbcSqlException) e).getCode());
    }
  }

  @Test
  public void testSetNullValues() throws SQLException {
    String sql = generateSqlWithParameters(27);
    try (JdbcPreparedStatement ps = new JdbcPreparedStatement(createMockConnection(), sql)) {
      ps.setNull(1, Types.BLOB);
      ps.setNull(2, Types.NVARCHAR);
      ps.setNull(4, Types.BINARY);
      ps.setNull(5, Types.BOOLEAN);
      ps.setNull(6, Types.TINYINT);
      ps.setNull(7, Types.DATE);
      ps.setNull(8, Types.DOUBLE);
      ps.setNull(9, Types.FLOAT);
      ps.setNull(10, Types.INTEGER);
      ps.setNull(11, Types.BIGINT);
      ps.setNull(12, Types.SMALLINT);
      ps.setNull(13, Types.TIME);
      ps.setNull(14, Types.TIMESTAMP);
      ps.setNull(15, Types.CHAR);
      ps.setNull(16, Types.CLOB);
      ps.setNull(17, Types.LONGNVARCHAR);
      ps.setNull(18, Types.LONGVARBINARY);
      ps.setNull(19, Types.LONGVARCHAR);
      ps.setNull(20, Types.NCHAR);
      ps.setNull(21, Types.NCLOB);
      ps.setNull(23, Types.NVARCHAR);
      ps.setNull(24, Types.REAL);
      ps.setNull(25, Types.BIT);
      ps.setNull(26, Types.VARBINARY);
      ps.setNull(27, Types.VARCHAR);

      JdbcParameterMetaData pmd = ps.getParameterMetaData();
      assertEquals(27, pmd.getParameterCount());
      assertEquals(Timestamp.class.getName(), pmd.getParameterClassName(14));

      ps.clearParameters();
      pmd = ps.getParameterMetaData();
      assertEquals(27, pmd.getParameterCount());
    }
  }

  @Test
  public void testGetResultSetMetadata() throws SQLException {
    final String sql = "SELECT * FROM FOO";
    Connection connection = mock(Connection.class);
    ResultSet rs =
        ResultSets.forRows(
            Type.struct(
                StructField.of("ID", Type.int64()),
                StructField.of("NAME", Type.string()),
                StructField.of("AMOUNT", Type.float64())),
            Arrays.asList(
                Struct.newBuilder()
                    .set("ID")
                    .to(1L)
                    .set("NAME")
                    .to("foo")
                    .set("AMOUNT")
                    .to(Math.PI)
                    .build()));
    when(connection.executeQuery(Statement.of(sql))).thenReturn(rs);
    try (JdbcPreparedStatement ps =
        new JdbcPreparedStatement(createMockConnection(connection), sql)) {
      ResultSetMetaData metadata = ps.getMetaData();
      assertEquals(3, metadata.getColumnCount());
      assertEquals("ID", metadata.getColumnLabel(1));
      assertEquals("NAME", metadata.getColumnLabel(2));
      assertEquals("AMOUNT", metadata.getColumnLabel(3));
      assertEquals(Types.BIGINT, metadata.getColumnType(1));
      assertEquals(Types.NVARCHAR, metadata.getColumnType(2));
      assertEquals(Types.DOUBLE, metadata.getColumnType(3));
    }
  }
}
