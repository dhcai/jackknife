/*
 * Copyright (C) 2017. The JackKnife Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lwh.jackknife.orm.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.lwh.jackknife.app.Application;
import com.lwh.jackknife.orm.AssignType;
import com.lwh.jackknife.orm.Transaction;
import com.lwh.jackknife.orm.annotation.Column;
import com.lwh.jackknife.orm.annotation.NonColumn;
import com.lwh.jackknife.orm.annotation.PrimaryKey;
import com.lwh.jackknife.orm.builder.QueryBuilder;
import com.lwh.jackknife.orm.builder.WhereBuilder;
import com.lwh.jackknife.orm.table.OrmTable;
import com.lwh.jackknife.orm.table.TableManager;
import com.lwh.jackknife.util.TextUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class OrmDao<T extends OrmTable> implements Dao<T> {

    private Class<T> mBeanClass;
    private SQLiteOpenHelper mHelper;
    private SQLiteDatabase mDatabase;
    private final String SELECT_COUNT = "SELECT COUNT(*) FROM ";

    /* package */ OrmDao(Class<T> beanClass) {
        this.mBeanClass = beanClass;
        this.mHelper = Application.getInstance().getSQLiteOpenHelper();
        this.mDatabase = mHelper.getWritableDatabase();
    }

    private boolean isAssignableFromBoolean(Class<?> fieldType) {
        return boolean.class.isAssignableFrom(fieldType) ||
                Boolean.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromByte(Class<?> fieldType) {
        return byte.class.isAssignableFrom(fieldType) || Byte.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromShort(Class<?> fieldType) {
        return short.class.isAssignableFrom(fieldType) || Short.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromInteger(Class<?> fieldType) {
        return int.class.isAssignableFrom(fieldType) || Integer.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromLong(Class<?> fieldType) {
        return long.class.isAssignableFrom(fieldType) || Long.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromFloat(Class<?> fieldType) {
        return float.class.isAssignableFrom(fieldType) || Float.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromDouble(Class<?> fieldType) {
        return double.class.isAssignableFrom(fieldType) || Double.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromCharacter(Class<?> fieldType) {
        return char.class.isAssignableFrom(fieldType) || Character.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromCharSequence(Class<?> fieldType) {
        return CharSequence.class.isAssignableFrom(fieldType);
    }

    private boolean isAssignableFromClass(Class<?> fieldType) {
        return Class.class.isAssignableFrom(fieldType);
    }

    public ContentValues getContentValues(T bean) {
        ContentValues values = new ContentValues();
        Field[] fields = mBeanClass.getDeclaredFields();
        for (Field field:fields) {
            field.setAccessible(true);
            NonColumn nonColumn = field.getAnnotation(NonColumn.class);
            Column column = field.getAnnotation(Column.class);
            PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
            if (nonColumn != null) {
                continue;
            }
            if (primaryKey != null && primaryKey.value() == AssignType.AUTO_INCREMENT) {
                continue;
            }
            String columnName;
            if (column != null) {
                columnName = column.value();
            } else {
                columnName = TableManager.getInstance().generateColumnName(field.getName());
            }
            Class<?> fieldType = field.getType();
            try {
                if (isAssignableFromCharSequence(fieldType)) {
                    values.put(columnName, String.valueOf(field.get(bean)));
                } else if (isAssignableFromBoolean(fieldType)) {
                    values.put(columnName, field.getBoolean(bean));
                } else if (isAssignableFromByte(fieldType)) {
                    values.put(columnName, field.getByte(bean));
                } else if (isAssignableFromShort(fieldType)) {
                    values.put(columnName, field.getShort(bean));
                } else if (isAssignableFromInteger(fieldType)) {
                    values.put(columnName, field.getInt(bean));
                } else if (isAssignableFromLong(fieldType)) {
                    values.put(columnName, field.getLong(bean));
                } else if (isAssignableFromFloat(fieldType)) {
                    values.put(columnName, field.getFloat(bean));
                } else if (isAssignableFromDouble(fieldType)) {
                    values.put(columnName, field.getDouble(bean));
                } else if (Class.class.isAssignableFrom(fieldType)) {
                    values.put(columnName, ((Class)field.get(bean)).getName());
                }
            }catch (IllegalAccessException e){
                e.printStackTrace();
            }
        }
        return values;
    }

    private String getColumns() {
        StringBuilder sb = new StringBuilder();
        Field[] fields = mBeanClass.getDeclaredFields();
        for (Field field:fields) {
            field.setAccessible(true);
            NonColumn nonColumn = field.getAnnotation(NonColumn.class);
            PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
            if (nonColumn == null && (primaryKey == null ||
                    (primaryKey != null && primaryKey.value() == AssignType.BY_MYSELF))) {
                String name = field.getName();
                sb.append(name).append(",");
            }
        }
        return sb.substring(0, sb.length()-2);
    }

    @Override
    public boolean insert(T bean) {
        TableManager manager = TableManager.getInstance();
        String tableName = manager.getTableName(mBeanClass);
        ContentValues contentValues = getContentValues(bean);
        return mDatabase.insert(tableName, getColumns(), contentValues) > 0;
    }

    @Override
    public boolean insert(List<T> beans) {
        int count = 0;
        for (T bean:beans) {
            boolean isOk = insert(bean);
            if (isOk) {
                count++;
            }
        }
        return count == beans.size();
    }

    @Override
    public boolean delete(final WhereBuilder builder) {
        return Transaction.execute(mDatabase, new Transaction.Worker() {
            @Override
            public boolean doTransition(SQLiteDatabase db) {
                TableManager manager = TableManager.getInstance();
                String tableName = manager.getTableName(mBeanClass);
                return mDatabase.delete(tableName, builder.getWhere(), convertWhereArgs(builder.getWhereArgs())) > 0;
            }
        });
    }

    @Override
    public boolean deleteAll() {
        return Transaction.execute(mDatabase, new Transaction.Worker() {
            @Override
            public boolean doTransition(SQLiteDatabase db) {
                TableManager manager = TableManager.getInstance();
                String tableName = manager.getTableName(mBeanClass);
                return mDatabase.delete(tableName, null, null) > 0;
            }
        });
    }

    public String[] convertWhereArgs(Object[] objects) {
        List<String> result = new ArrayList<>();
        for (Object obj:objects) {
            if (obj instanceof Number) {
                result.add(String.valueOf(obj));
            } else if (obj instanceof String) {
                result.add(obj.toString());
            }
        }
        return result.toArray(new String[]{});
    }

    @Override
    public boolean update(final WhereBuilder builder, final T newBean) {
        return Transaction.execute(mDatabase, new Transaction.Worker() {
            @Override
            public boolean doTransition(SQLiteDatabase db) {
                TableManager manager = TableManager.getInstance();
                String tableName = manager.getTableName(mBeanClass);
                ContentValues contentValues = getContentValues(newBean);
                return mDatabase.update(tableName, contentValues, builder.getWhere(), convertWhereArgs
                        (builder.getWhereArgs())) > 0;
            }
        });
    }

    @Override
    public boolean updateAll(final T newBean) {
        return Transaction.execute(mDatabase, new Transaction.Worker() {
            @Override
            public boolean doTransition(SQLiteDatabase db) {
                TableManager manager = TableManager.getInstance();
                String tableName = manager.getTableName(mBeanClass);
                ContentValues contentValues = getContentValues(newBean);
                return mDatabase.update(tableName, contentValues, null, null) > 0;
            }
        });
    }

    @Override
    public List<T> selectAll() {
        TableManager manager = TableManager.getInstance();
        String tableName = manager.getTableName(mBeanClass);
        Cursor cursor = mDatabase.query(tableName, null, null, null, null, null, null);
        return getResult(cursor);
    }

    @Override
    public List<T> select(QueryBuilder builder) {
        TableManager manager = TableManager.getInstance();
        String tableName = manager.getTableName(mBeanClass);
        String[] columns = builder.getColumns();
        String group = builder.getGroup();
        String having = builder.getHaving();
        String order = builder.getOrder();
        String limit = builder.getLimit();
        WhereBuilder whereBuilder = builder.getWhereBuilder();
        String where = whereBuilder.getWhere();
        String[] whereArgs = convertWhereArgs(whereBuilder.getWhereArgs());
        Cursor cursor = mDatabase.query(tableName, columns, where, whereArgs, group, having, order);
        List<T> result = getResult(cursor);
        if (TextUtils.isNotEmpty(limit) && limit.startsWith(QueryBuilder.LIMIT)) {
            if (limit.contains(",")) {
                String[] limitPart = limit.split(",");
                return getLimitedBeans(result, Integer.valueOf(limitPart[0]), Integer.valueOf(limitPart[1]));
            } else {
                return getLimitedBeans(result, Integer.valueOf(limit));
            }
        }
        return result;
    }

    @Override
    public T selectOne() {
        List<T> beans = selectAll();
        if (beans.size() > 0) {
            return beans.get(0);
        }
        return null;
    }

    @Override
    public T selectOne(QueryBuilder builder) {
        List<T> beans = select(builder);
        if (beans.size() > 0) {
            return beans.get(0);
        }
        return null;
    }

    public List<T> getLimitedBeans(List<T> beans, int start, int length) {
        List<T> newBeans = new ArrayList<>();
        for (int i=start;i<length;i++) {
            newBeans.add(beans.get(i));
        }
        return newBeans;
    }

    public List<T> getLimitedBeans(List<T> beans, int limit) {
        return getLimitedBeans(beans, 0, limit);
    }

    @Override
    public int selectAllCount() {
        List<T> beans = selectAll();
        return beans.size();
    }

    @Override
    public int selectCount(QueryBuilder builder) {
        List<T> beans = select(builder);
        return beans.size();
    }

    public List<T> getResult(Cursor cursor) {
        List<T> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            try {
                T bean = mBeanClass.newInstance();
                Field[] fields = mBeanClass.getDeclaredFields();
                for (Field field:fields) {
                    field.setAccessible(true);
                    String columnName;
                    Column column = field.getAnnotation(Column.class);
                    if (column != null) {
                        columnName = column.value();
                    } else {
                        columnName = TableManager.getInstance().generateColumnName(field.getName());
                    }
                    int columnIndex = cursor.getColumnIndex(columnName);
                    if (columnIndex != -1) {
                        Class<?> fieldType = field.getType();
                        if (isAssignableFromCharSequence(fieldType)) {
                            field.set(bean, cursor.getString(columnIndex));
                        } else if (isAssignableFromBoolean(fieldType)) {
                            int value = cursor.getInt(columnIndex);
                            switch (value) {
                                case 0:
                                    field.set(bean, false);
                                    break;
                                case 1:
                                    field.set(bean, true);
                                    break;
                            }
                        } else if (isAssignableFromLong(fieldType)) {
                            field.set(bean, cursor.getLong(columnIndex));
                        } else if (isAssignableFromInteger(fieldType)) {
                            field.set(bean, cursor.getInt(columnIndex));
                        } else if (isAssignableFromShort(fieldType)
                                || isAssignableFromByte(fieldType)) {
                            field.set(bean, cursor.getShort(columnIndex));
                        } else if (isAssignableFromDouble(fieldType)) {
                            field.set(bean, cursor.getDouble(columnIndex));
                        } else if (isAssignableFromFloat(fieldType)) {
                            field.set(bean, cursor.getFloat(columnIndex));
                        } else if (isAssignableFromCharacter(fieldType)) {
                            field.set(bean, cursor.getString(columnIndex));
                        } else if (isAssignableFromClass(fieldType)) {
                            try {
                                field.set(bean, Class.forName(cursor.getString(columnIndex)));
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        } else {
                            field.set(bean, cursor.getBlob(columnIndex));
                        }
                    }
                }
                result.add(bean);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
