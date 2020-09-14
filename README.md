
&emsp;&emsp;前段时间在对一个需求进行实现的过程中使用到了ormLite框架，这其中就会涉及到对数据库版本进行升级的处理。新增/删除字段，又或者是新增/删除数据表，都需要对数据库的版本进行升级。最简单暴力的做法就是在`OrmLiteSqliteOpenHelper#onUpgrade()`中将需要更新的数据表删除，再重新创建新的数据表。这种方式虽然简单有效，但是会导致数据表中的数据丢失，这将会严重影响到用户的使用体验。所以应该要在保留已有数据的前提下进行版本升级。以下是在参考了别人的实现后做了一些改动，如有不合理的地方或者更好的方案欢迎指正。[源码下载](https://github.com/zjjjia/DbVersionManager)


* 新增数据表</br>
&emsp;&emsp;在`OrmLiteHlper`的构造函数中，通过反射的方式遍历包下的类，获取到标注有注解`@DatabaseTable`的bean类的`Class`对象实例列表，然后在`onCreate()`中通过遍历对象实例的列表对不存在的数据表进行创建数据表的操作；不过在增加新的bean类后，还需要修改数据库版本号后`DatebaseHelper`类才会调用`onUpgrade()`，并在该方法中调用`onCreate()`来创建新的数据表。

```java
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static DatabaseHelper instance;
    //数据库名
    private static String DATABASE_NAME = "mydb.db";
    //数据库版本号
    private static int DATABASE_VERSION = 20;

    private Map<String, Dao> daoMap;
    private ArrayList<Class<?>> clazzList;

    static DatabaseHelper getInstance(Context context) {
        //此处代码省略……，完整源码：https://github.com/zjjjia/DbVersionManager
        .....
        return instance;
    }

    public synchronized Dao getDao(Class clazz) throws SQLException {
        //省略……
        .....
        return dao;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        daoMap = new HashMap<>();
        clazzList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        	//获取bean包下所有数据表bean类的Class对象实例的列表
            clazzList.addAll(DatabaseUtil.getClasses(context, "com.example.dbversionmanager.bean"));
        }
        Log.d(TAG, "DatabaseHelper");
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource) {

        Log.d(TAG, "onCreate");
        if (clazzList != null) {
            for (Class<?> clazz : clazzList) {
                try {
               		//遍历bean类对象实例的列表，对应数据表不存在则创建该数据表
                    if (!DatabaseUtil.tableIsExist(sqLiteDatabase, DatabaseUtil.extractTableName(clazz))) {
                        TableUtils.createTable(connectionSource, clazz);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        for (Class clazz : clazzList) {
         	//遍历bean类对象实例的列表，如果数据表存在则更新数据表
            if (DatabaseUtil.tableIsExist(sqLiteDatabase, DatabaseUtil.extractTableName(clazz))) {
                DatabaseUtil.updateTable(sqLiteDatabase, connectionSource, clazz);
            } else { //不存在则调用onCreate()创建数据表
                onCreate(sqLiteDatabase, connectionSource);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        //部分代码省略……
        ....
    }
}
```

```java
//DatabaseUtil.java
// 获取包下面被注解@DatabaseTable标注的bean类的Class对象的列表
@RequiresApi(api = Build.VERSION_CODES.N)
public static ArrayList<Class<?>> getClasses(Context mContext, String packageName) {
	ArrayList<Class<?>> classes = new ArrayList<>();
    try {
        String packageCodePath = mContext.getPackageCodePath();
        DexFile df = new DexFile(packageCodePath);
        String regExp = "^" + packageName + ".\\w+$";
        for (Enumeration iter = df.entries(); iter.hasMoreElements(); ) {
          	String className = (String) iter.nextElement();
            if (className.matches(regExp)) {
            	Class clazz = Class.forName(className);
                if (clazz.getDeclaredAnnotation(DatabaseTable.class) != null) {
               		classes.add(clazz);
                }
            }
        }
	} catch (IOException | ClassNotFoundException e) {
        e.printStackTrace();
    }
    return classes;
}
```

* 数据表中新增/删除列<br>
&emsp;&emsp;将原数据表重命名为临时数据表，然后在创建新的数据表并将原有数据从临时表中拷贝到新建表中。以下为局部方法：
```java
//DatabaseUtil.java
//更新数据表
public static <T> void updateTable(SQLiteDatabase db, ConnectionSource connectionSource, Class<T> clazz) {
        String tableName = extractTableName(clazz);

        db.beginTransaction();
        //将原有数据表重命名为一个临时表
        try {
            String tempTableName = tableName + "_temp";
            String sql = "ALTER TABLE " + tableName + " RENAME TO " + tempTableName;
            db.execSQL(sql);
            Log.d(TAG, "updateTable: " + sql);

            sql = TableUtils.getCreateTableStatements(connectionSource, clazz).get(0);
            db.execSQL(sql);

			//获取需拷贝数据表中字段的字符串形式
            String columns = getCopyColumnsStr(db, tableName, tempTableName);

			//有多张数据表的时候，并不一定所有数据表都会有发生改变，所以对于未发生变化的数据表无需进行数据的拷贝
            //columns 为空说明该表没有发生变化，不需要创建新的数据表，删除新建表并将临时表的表名改回来
            if (columns == null) {
                sql = "DROP TABLE IF EXISTS " + tableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);
                sql = "ALTER TABLE " + tempTableName + " RENAME TO " + tableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);
            } else {  //数据表的列发生了变化，将原表中的数据拷贝到新表中，并将临时表删除
                sql = "INSERT INTO " + tableName + " (" + columns + ") SELECT " + columns + " FROM " + tempTableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);
                //删除临时表
                sql = "DROP TABLE IF EXISTS " + tempTableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);

            }
            db.setTransactionSuccessful();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }
```
