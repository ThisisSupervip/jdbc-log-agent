package com.vip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Wraps a PreparedStatement and reports method calls, returns and exceptions.
 *
 * @author Arthur Blake
 */
public class PreparedStatementProxy implements PreparedStatement {

    /**
     * holds list of bind variables for tracing
     */
    protected final List argTrace = new ArrayList();

    protected static final String dateFormat = "MM/dd/yyyy HH:mm:ss.SSS";

    // a way to turn on and off type help...
    // todo:  make this a configurable parameter
    // todo, debug arrays and streams in a more useful manner.... if possible
    private static final boolean showTypeHelp = false;
    private static final Logger log = LoggerFactory.getLogger(PreparedStatementProxy.class);

    /**
     * Store an argument (bind variable) into the argTrace list (above) for later dumping.
     *
     * @param i          index of argument being set.
     * @param typeHelper optional additional info about the type that is being set in the arg
     * @param arg        argument being bound.
     */
    protected void argTraceSet(int i, String typeHelper, Object arg) {
        String tracedArg;
        try {
            tracedArg = formatParameterObject(arg);
        } catch (Throwable t) {
            // rdbmsSpecifics should NEVER EVER throw an exception!!
            // but just in case it does, we trap it.
            log.debug("rdbmsSpecifics threw an exception while trying to format a " +
                    "parameter object [" + arg + "] this is very bad!!! (" +
                    t.getMessage() + ")");

            // backup - so that at least we won't harm the application using us
            tracedArg = arg == null ? "null" : arg.toString();
        }

        i--;  // make the index 0 based
        synchronized (argTrace) {
            // if an object is being inserted out of sequence, fill up missing values with null...
            while (i >= argTrace.size()) {
                argTrace.add(argTrace.size(), null);
            }
            if (!showTypeHelp || typeHelper == null) {
                argTrace.set(i, tracedArg);
            } else {
                argTrace.set(i, typeHelper + tracedArg);
            }
        }
    }

    /**
     * Format an Object that is being bound to a PreparedStatement parameter, for display. The goal is to reformat the
     * object in a format that can be re-run against the native SQL client of the particular Rdbms being used.  This
     * class should be extended to provide formatting instances that format objects correctly for different RDBMS
     * types.
     *
     * @param object jdbc object to be formatted.
     * @return formatted dump of the object.
     */
    String formatParameterObject(Object object) {
        if (object == null) {
            return "NULL";
        } else {
            if (object instanceof String) {
                return "'" + escapeString((String) object) + "'";
            } else if (object instanceof Date) {
                return "'" + new SimpleDateFormat(dateFormat).format(object) + "'";
            } else if (object instanceof Boolean) {
                return true ?
                        ((Boolean) object).booleanValue() ? "true" : "false"
                        : ((Boolean) object).booleanValue() ? "1" : "0";
            } else {
                return object.toString();
            }
        }
    }

    /**
     * Make sure string is escaped properly so that it will run in a SQL query analyzer tool.
     * At this time all we do is double any single tick marks.
     * Do not call this with a null string or else an exception will occur.
     *
     * @return the input String, escaped.
     */
    String escapeString(String in) {
        StringBuilder out = new StringBuilder();
        for (int i = 0, j = in.length(); i < j; i++) {
            char c = in.charAt(i);
            if (c == '\'') {
                out.append(c);
            }
            out.append(c);
        }
        return out.toString();
    }

    private String sql;

    protected String dumpedSql() {
        StringBuffer dumpSql = new StringBuffer();
        int lastPos = 0;
        int Qpos = sql.indexOf('?', lastPos);  // find position of first question mark
        int argIdx = 0;
        String arg;

        while (Qpos != -1) {
            // get stored argument
            synchronized (argTrace) {
                try {
                    arg = (String) argTrace.get(argIdx);
                } catch (IndexOutOfBoundsException e) {
                    arg = "?";
                }
            }
            if (arg == null) {
                arg = "?";
            }

            argIdx++;

            dumpSql.append(sql.substring(lastPos, Qpos));  // dump segment of sql up to question mark.
            lastPos = Qpos + 1;
            Qpos = sql.indexOf('?', lastPos);
            dumpSql.append(arg);
        }
        if (lastPos < sql.length()) {
            dumpSql.append(sql.substring(lastPos, sql.length()));  // dump last segment
        }

        return dumpSql.toString();
    }

    /*protected void reportAllReturns(String methodCall, String msg) {
        log.methodReturned(this, methodCall, msg);
    }*/

    /**
     * The real PreparedStatement that this PreparedStatementSpy wraps.
     */
    protected PreparedStatement realPreparedStatement;

    /**
     * Get the real PreparedStatement that this PreparedStatementSpy wraps.
     *
     * @return the real PreparedStatement that this PreparedStatementSpy wraps.
     */
    public PreparedStatement getRealPreparedStatement() {
        return realPreparedStatement;
    }


    /**
     * Create a PreparedStatementSpy (JDBC 4 version) for logging activity of another PreparedStatement.
     *
     * @param sql                   SQL for the prepared statement that is being spied upon.
     * @param realPreparedStatement The actual PreparedStatement that is being spied upon.
     */
    public PreparedStatementProxy(String sql, PreparedStatement realPreparedStatement) {
        this.sql = sql;
        this.realPreparedStatement = realPreparedStatement;
    }

    public String getClassType() {
        return "PreparedStatement";
    }

    // forwarding methods

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        String methodCall = "setTime(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(Time)", x);
        realPreparedStatement.setTime(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        String methodCall = "setTime(" + parameterIndex + ", " + x + ", " + cal + ")";
        argTraceSet(parameterIndex, "(Time)", x);
        realPreparedStatement.setTime(parameterIndex, x, cal);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        String methodCall = "setCharacterStream(" + parameterIndex + ", " + reader + ", " + length + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
        realPreparedStatement.setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        String methodCall = "setNull(" + parameterIndex + ", " + sqlType + ")";
        argTraceSet(parameterIndex, null, null);
        realPreparedStatement.setNull(parameterIndex, sqlType);
    }

    @Override
    public void setNull(int paramIndex, int sqlType, String typeName) throws SQLException {
        String methodCall = "setNull(" + paramIndex + ", " + sqlType + ", " + typeName + ")";
        argTraceSet(paramIndex, null, null);
        realPreparedStatement.setNull(paramIndex, sqlType, typeName);
    }

    @Override
    public void setRef(int i, Ref x) throws SQLException {
        String methodCall = "setRef(" + i + ", " + x + ")";
        argTraceSet(i, "(Ref)", x);
        realPreparedStatement.setRef(i, x);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        String methodCall = "setBoolean(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(boolean)", x ? Boolean.TRUE : Boolean.FALSE);
        realPreparedStatement.setBoolean(parameterIndex, x);
    }

    @Override
    public void setBlob(int i, Blob x) throws SQLException {
        String methodCall = "setBlob(" + i + ", " + x + ")";
        argTraceSet(i, "(Blob)",
                x == null ? null : ("<Blob of size " + x.length() + ">"));
        realPreparedStatement.setBlob(i, x);
    }

    @Override
    public void setClob(int i, Clob x) throws SQLException {
        String methodCall = "setClob(" + i + ", " + x + ")";
        argTraceSet(i, "(Clob)",
                x == null ? null : ("<Clob of size " + x.length() + ">"));
        realPreparedStatement.setClob(i, x);
    }

    @Override
    public void setArray(int i, Array x) throws SQLException {
        String methodCall = "setArray(" + i + ", " + x + ")";
        argTraceSet(i, "(Array)", "<Array>");
        realPreparedStatement.setArray(i, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        String methodCall = "setByte(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(byte)", new Byte(x));
        realPreparedStatement.setByte(parameterIndex, x);
    }

    /**
     * @deprecated
     */
    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        String methodCall = "setUnicodeStream(" + parameterIndex + ", " + x + ", " + length + ")";
        argTraceSet(parameterIndex, "(Unicode InputStream)", "<Unicode InputStream of length " + length + ">");
        realPreparedStatement.setUnicodeStream(parameterIndex, x, length);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        String methodCall = "setShort(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(short)", new Short(x));
        realPreparedStatement.setShort(parameterIndex, x);
    }

    @Override
    public boolean execute() throws SQLException {
        String methodCall = "execute()";
        String dumpedSql = dumpedSql();
        reportSql(dumpedSql, methodCall);
        return realPreparedStatement.execute();
    }

    private void reportSql(String dumpedSql, String methodCall) {
        log.info("\n+++++++++++++++++++++++++++"+ methodCall +"++++++++++++++++++++++++++++\n" +
                dumpedSql + "\n" +
                "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        String methodCall = "setInt(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(int)", new Integer(x));
        realPreparedStatement.setInt(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        String methodCall = "setLong(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(long)", new Long(x));
        realPreparedStatement.setLong(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        String methodCall = "setFloat(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(float)", new Float(x));
        realPreparedStatement.setFloat(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        String methodCall = "setDouble(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(double)", new Double(x));
        realPreparedStatement.setDouble(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        String methodCall = "setBigDecimal(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(BigDecimal)", x);
        realPreparedStatement.setBigDecimal(parameterIndex, x);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        String methodCall = "setURL(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(URL)", x);
        realPreparedStatement.setURL(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        String methodCall = "setString(" + parameterIndex + ", \"" + x + "\")";
        argTraceSet(parameterIndex, "(String)", x);

        realPreparedStatement.setString(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        //todo: dump array?
        String methodCall = "setBytes(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(byte[])", "<byte[]>");
        realPreparedStatement.setBytes(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        String methodCall = "setDate(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(Date)", x);
        realPreparedStatement.setDate(parameterIndex, x);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        String methodCall = "getParameterMetaData()";
        return realPreparedStatement.getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        String methodCall = "setRowId(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(RowId)", x);
        realPreparedStatement.setRowId(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        String methodCall = "setNString(" + parameterIndex + ", " + value + ")";
        argTraceSet(parameterIndex, "(String)", value);
        realPreparedStatement.setNString(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        String methodCall = "setNCharacterStream(" + parameterIndex + ", " + value + ", " + length + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
        realPreparedStatement.setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        String methodCall = "setNClob(" + parameterIndex + ", " + value + ")";
        argTraceSet(parameterIndex, "(NClob)", "<NClob>");
        realPreparedStatement.setNClob(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        String methodCall = "setClob(" + parameterIndex + ", " + reader + ", " + length + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
        realPreparedStatement.setClob(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        String methodCall = "setBlob(" + parameterIndex + ", " + inputStream + ", " + length + ")";
        argTraceSet(parameterIndex, "(InputStream)", "<InputStream of length " + length + ">");
        realPreparedStatement.setBlob(parameterIndex, inputStream, length);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        String methodCall = "setNClob(" + parameterIndex + ", " + reader + ", " + length + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
        realPreparedStatement.setNClob(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        String methodCall = "setSQLXML(" + parameterIndex + ", " + xmlObject + ")";
        argTraceSet(parameterIndex, "(SQLXML)", xmlObject);
        realPreparedStatement.setSQLXML(parameterIndex, xmlObject);
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        String methodCall = "setDate(" + parameterIndex + ", " + x + ", " + cal + ")";
        argTraceSet(parameterIndex, "(Date)", x);
        realPreparedStatement.setDate(parameterIndex, x, cal);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        String methodCall = "executeQuery()";
        String dumpedSql = dumpedSql();
        reportSql(dumpedSql, methodCall);
        return realPreparedStatement.executeQuery();
    }

    private String getTypeHelp(Object x) {
        if (x == null) {
            return "(null)";
        } else {
            return "(" + x.getClass().getName() + ")";
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException {
        String methodCall = "setObject(" + parameterIndex + ", " + x + ", " + targetSqlType + ", " + scale + ")";
        argTraceSet(parameterIndex, getTypeHelp(x), x);

        realPreparedStatement.setObject(parameterIndex, x, targetSqlType, scale);
    }

    /**
     * Sets the designated parameter to the given input stream, which will have
     * the specified number of bytes.
     * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.InputStream</code>. Data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from ASCII to the database char format.
     * <p/>
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     *
     * @param parameterIndex the first parameter is 1, the second is 2, ...
     * @param x              the Java input stream that contains the ASCII parameter value
     * @param length         the number of bytes in the stream
     * @throws java.sql.SQLException if parameterIndex does not correspond to a parameter
     *                               marker in the SQL statement; if a database access error occurs or
     *                               this method is called on a closed <code>PreparedStatement</code>
     * @since 1.6
     */
    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        String methodCall = "setAsciiStream(" + parameterIndex + ", " + x + ", " + length + ")";
        argTraceSet(parameterIndex, "(Ascii InputStream)", "<Ascii InputStream of length " + length + ">");
        realPreparedStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        String methodCall = "setBinaryStream(" + parameterIndex + ", " + x + ", " + length + ")";
        argTraceSet(parameterIndex, "(Binary InputStream)", "<Binary InputStream of length " + length + ">");
        realPreparedStatement.setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        String methodCall = "setCharacterStream(" + parameterIndex + ", " + reader + ", " + length + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
        realPreparedStatement.setCharacterStream(parameterIndex, reader, length);

    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        String methodCall = "setAsciiStream(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(Ascii InputStream)", "<Ascii InputStream>");
        realPreparedStatement.setAsciiStream(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        String methodCall = "setBinaryStream(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(Binary InputStream)", "<Binary InputStream>");
        realPreparedStatement.setBinaryStream(parameterIndex, x);

    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        String methodCall = "setCharacterStream(" + parameterIndex + ", " + reader + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader>");
        realPreparedStatement.setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        String methodCall = "setNCharacterStream(" + parameterIndex + ", " + reader + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader>");
        realPreparedStatement.setNCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        String methodCall = "setClob(" + parameterIndex + ", " + reader + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader>");
        realPreparedStatement.setClob(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        String methodCall = "setBlob(" + parameterIndex + ", " + inputStream + ")";
        argTraceSet(parameterIndex, "(InputStream)", "<InputStream>");
        realPreparedStatement.setBlob(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        String methodCall = "setNClob(" + parameterIndex + ", " + reader + ")";
        argTraceSet(parameterIndex, "(Reader)", "<Reader>");
        realPreparedStatement.setNClob(parameterIndex, reader);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        String methodCall = "setObject(" + parameterIndex + ", " + x + ", " + targetSqlType + ")";
        argTraceSet(parameterIndex, getTypeHelp(x), x);
        realPreparedStatement.setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        String methodCall = "setObject(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, getTypeHelp(x), x);
        realPreparedStatement.setObject(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        String methodCall = "setTimestamp(" + parameterIndex + ", " + x + ")";
        argTraceSet(parameterIndex, "(Date)", x);
        realPreparedStatement.setTimestamp(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        String methodCall = "setTimestamp(" + parameterIndex + ", " + x + ", " + cal + ")";
        argTraceSet(parameterIndex, "(Timestamp)", x);
        realPreparedStatement.setTimestamp(parameterIndex, x, cal);
    }

    @Override
    public int executeUpdate() throws SQLException {
        String methodCall = "executeUpdate()";
        String dumpedSql = dumpedSql();
        reportSql(dumpedSql, methodCall);
        return realPreparedStatement.executeUpdate();
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        String methodCall = "setAsciiStream(" + parameterIndex + ", " + x + ", " + length + ")";
        argTraceSet(parameterIndex, "(Ascii InputStream)", "<Ascii InputStream of length " + length + ">");
        realPreparedStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        String methodCall = "setBinaryStream(" + parameterIndex + ", " + x + ", " + length + ")";
        argTraceSet(parameterIndex, "(Binary InputStream)", "<Binary InputStream of length " + length + ">");
        realPreparedStatement.setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void clearParameters() throws SQLException {
        String methodCall = "clearParameters()";

        synchronized (argTrace) {
            argTrace.clear();
        }

        realPreparedStatement.clearParameters();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        String methodCall = "getMetaData()";
        return realPreparedStatement.getMetaData();
    }

    @Override
    public void addBatch() throws SQLException {
        String methodCall = "addBatch()";
        realPreparedStatement.addBatch();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        String methodCall = "unwrap(" + (iface == null ? "null" : iface.getName()) + ")";
        return realPreparedStatement.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        String methodCall = "isWrapperFor(" + (iface == null ? "null" : iface.getName()) + ")";
        return realPreparedStatement.isWrapperFor(iface);
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
        realPreparedStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
        realPreparedStatement.setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        String methodCall = "executeLargeUpdate()";
        String dumpedSql = dumpedSql();
        reportSql(dumpedSql, methodCall);
        return realPreparedStatement.executeLargeUpdate();
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return realPreparedStatement.executeQuery(sql);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return realPreparedStatement.executeUpdate(sql);
    }

    @Override
    public void close() throws SQLException {
        realPreparedStatement.close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return realPreparedStatement.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        realPreparedStatement.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return realPreparedStatement.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        realPreparedStatement.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        realPreparedStatement.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return realPreparedStatement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        realPreparedStatement.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        realPreparedStatement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return realPreparedStatement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        realPreparedStatement.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        realPreparedStatement.setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return realPreparedStatement.execute(sql);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return realPreparedStatement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return realPreparedStatement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return realPreparedStatement.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        realPreparedStatement.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return realPreparedStatement.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        realPreparedStatement.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return realPreparedStatement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return realPreparedStatement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return realPreparedStatement.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        realPreparedStatement.addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        realPreparedStatement.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return realPreparedStatement.executeBatch();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return realPreparedStatement.getConnection();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return realPreparedStatement.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return realPreparedStatement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return realPreparedStatement.executeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return realPreparedStatement.executeUpdate(sql, columnIndexes);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return realPreparedStatement.executeUpdate(sql, columnNames);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return realPreparedStatement.execute(sql, autoGeneratedKeys);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return realPreparedStatement.execute(sql, columnIndexes);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return realPreparedStatement.execute(sql, columnNames);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return realPreparedStatement.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return realPreparedStatement.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        realPreparedStatement.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return realPreparedStatement.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        realPreparedStatement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return realPreparedStatement.isCloseOnCompletion();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        return realPreparedStatement.getLargeUpdateCount();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        realPreparedStatement.setLargeMaxRows(max);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        return realPreparedStatement.getLargeMaxRows();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        return realPreparedStatement.executeLargeBatch();
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        return realPreparedStatement.executeLargeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return realPreparedStatement.executeLargeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return realPreparedStatement.executeLargeUpdate(sql, columnIndexes);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return realPreparedStatement.executeLargeUpdate(sql, columnNames);
    }
}

