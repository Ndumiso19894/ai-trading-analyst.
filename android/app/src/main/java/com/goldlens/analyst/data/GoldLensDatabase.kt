package com.goldlens.analyst.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.goldlens.analyst.core.AnalysisResult
import com.goldlens.analyst.core.Candle
import com.goldlens.analyst.core.DataSourceKind
import com.goldlens.analyst.core.PriceZone

@Entity(tableName = "market_candles", primaryKeys = ["symbol", "timeframe", "timestamp_millis"], indices = [Index(value = ["timeframe", "timestamp_millis"])])
data class CandleEntity(
    val symbol: String = "XAU/USD",
    val timeframe: String,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    @ColumnInfo(defaultValue = "'IMPORTED_CSV'") val source: String = "IMPORTED_CSV",
    @ColumnInfo(name = "source_label", defaultValue = "'Imported CSV'") val sourceLabel: String = "Imported CSV",
    @ColumnInfo(name = "source_precision") val sourcePrecision: Int? = null,
)

@Entity(tableName = "analysis_records", indices = [Index(value = ["timestamp_millis"])])
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
    val symbol: String = "XAU/USD",
    val timeframe: String,
    val source: String,
    val direction: String,
    val score: Int,
    val price: Double?,
    val entry: Double?,
    @ColumnInfo(name = "stop_loss") val stopLoss: Double?,
    @ColumnInfo(name = "take_profit") val takeProfit: Double?,
    @ColumnInfo(name = "reward_risk") val rewardRisk: Double?,
    val reason: String,
    val evidence: String,
    val conflicts: String,
)

@Entity(
    tableName = "analysis_zones",
    primaryKeys = ["analysis_id", "side", "midpoint"],
    foreignKeys = [ForeignKey(entity = AnalysisEntity::class, parentColumns = ["id"], childColumns = ["analysis_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("analysis_id")],
)
data class ZoneEntity(
    @ColumnInfo(name = "analysis_id") val analysisId: Long,
    val side: String,
    val lower: Double,
    val upper: Double,
    val midpoint: Double,
    val touches: Int,
    val strength: String,
    val timeframe: String,
    val status: String,
    @ColumnInfo(name = "last_touch_millis") val lastTouchMillis: Long,
)

@Entity(tableName = "trade_journal", indices = [Index(value = ["opened_at_millis"])])
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "opened_at_millis") val openedAtMillis: Long,
    @ColumnInfo(name = "closed_at_millis") val closedAtMillis: Long? = null,
    val symbol: String = "XAU/USD",
    val direction: String,
    val timeframe: String,
    val entry: Double,
    @ColumnInfo(name = "stop_loss") val stopLoss: Double?,
    @ColumnInfo(name = "take_profit") val takeProfit: Double?,
    @ColumnInfo(name = "exit_price") val exitPrice: Double? = null,
    val notes: String = "",
)

@Entity(tableName = "backtest_runs", indices = [Index(value = ["created_at_millis"])])
data class BacktestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    val strategy: String,
    val timeframe: String,
    val source: String,
    val trades: Int,
    val winRate: Double?,
    val profitFactor: Double?,
    val maxDrawdown: Double?,
    val netResult: Double?,
    val reportJson: String,
)

@Entity(tableName = "user_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "alert_rules", indices = [Index(value = ["enabled", "timeframe"])])
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timeframe: String,
    val threshold: Double? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "minimum_interval_minutes") val minimumIntervalMinutes: Int = 60,
    @ColumnInfo(name = "last_fired_millis") val lastFiredMillis: Long? = null,
)

@Entity(tableName = "signal_outcomes", indices = [
    Index(value = ["symbol", "opened_at_millis"]),
    Index(value = ["outcome"]),
])
data class SignalOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val timeframe: String,
    val direction: String,
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val score: Int,
    val rewardRisk: Double?,
    @ColumnInfo(name = "opened_at_millis") val openedAtMillis: Long,
    val outcome: String?,
    @ColumnInfo(name = "closed_at_millis") val closedAtMillis: Long?,
    val exitPrice: Double?,
)

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(candles: List<CandleEntity>)

    @Query("SELECT * FROM market_candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestamp_millis DESC LIMIT :limit")
    suspend fun latest(symbol: String, timeframe: String, limit: Int): List<CandleEntity>

    @Query("DELETE FROM market_candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun delete(symbol: String, timeframe: String)

    @Query("SELECT COUNT(*) FROM market_candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun count(symbol: String, timeframe: String): Int
}

@Dao
interface AnalysisDao {
    @Insert
    suspend fun insert(result: AnalysisEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<ZoneEntity>)

    @Query("SELECT * FROM analysis_records ORDER BY timestamp_millis DESC LIMIT :limit")
    suspend fun history(limit: Int): List<AnalysisEntity>

    @Query("SELECT * FROM analysis_zones WHERE analysis_id = :analysisId ORDER BY side, midpoint")
    suspend fun zones(analysisId: Long): List<ZoneEntity>
}

@Dao
interface JournalDao {
    @Insert
    suspend fun insert(entry: JournalEntity): Long

    @Query("SELECT * FROM trade_journal ORDER BY opened_at_millis DESC LIMIT :limit")
    suspend fun history(limit: Int): List<JournalEntity>

    @Query("UPDATE trade_journal SET closed_at_millis = :closedAt, exit_price = :exitPrice WHERE id = :id")
    suspend fun close(id: Long, closedAt: Long, exitPrice: Double)
}

@Dao
interface BacktestDao {
    @Insert
    suspend fun insert(run: BacktestEntity): Long

    @Query("SELECT * FROM backtest_runs ORDER BY created_at_millis DESC LIMIT :limit")
    suspend fun history(limit: Int): List<BacktestEntity>
}

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT value FROM user_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?
}

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AlertRuleEntity): Long

    @Query("SELECT * FROM alert_rules ORDER BY id")
    suspend fun all(): List<AlertRuleEntity>

    @Query("SELECT * FROM alert_rules WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<AlertRuleEntity>

    @Query("UPDATE alert_rules SET last_fired_millis = :firedAt WHERE id = :id")
    suspend fun markFired(id: Long, firedAt: Long)
}

@Dao
interface SignalOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outcome: SignalOutcomeEntity): Long

    @Query("SELECT * FROM signal_outcomes WHERE symbol = :symbol ORDER BY opened_at_millis DESC LIMIT :limit")
    suspend fun history(symbol: String, limit: Int): List<SignalOutcomeEntity>

    @Query("SELECT * FROM signal_outcomes ORDER BY opened_at_millis DESC LIMIT :limit")
    suspend fun all(limit: Int): List<SignalOutcomeEntity>

    @Query("UPDATE signal_outcomes SET outcome = :outcome, closed_at_millis = :closedAt, exit_price = :exitPrice WHERE id = :id")
    suspend fun updateOutcome(id: Long, outcome: String?, closedAt: Long?, exitPrice: Double?)
}

@Database(
    entities = [CandleEntity::class, AnalysisEntity::class, ZoneEntity::class, JournalEntity::class,
        BacktestEntity::class, SettingEntity::class, AlertRuleEntity::class, SignalOutcomeEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class GoldLensDatabase : RoomDatabase() {
    abstract fun candleDao(): CandleDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun journalDao(): JournalDao
    abstract fun backtestDao(): BacktestDao
    abstract fun settingDao(): SettingDao
    abstract fun alertDao(): AlertDao
    abstract fun signalOutcomeDao(): SignalOutcomeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE market_candles ADD COLUMN source TEXT NOT NULL DEFAULT 'IMPORTED_CSV'")
                database.execSQL("ALTER TABLE market_candles ADD COLUMN source_label TEXT NOT NULL DEFAULT 'Imported CSV'")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE market_candles ADD COLUMN source_precision INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS signal_outcomes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        symbol TEXT NOT NULL,
                        timeframe TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        entry REAL,
                        stop_loss REAL,
                        take_profit REAL,
                        score INTEGER NOT NULL,
                        reward_risk REAL,
                        opened_at_millis INTEGER NOT NULL,
                        outcome TEXT,
                        closed_at_millis INTEGER,
                        exit_price REAL
                    )""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_signal_outcomes_symbol_opened_at_millis ON signal_outcomes (symbol, opened_at_millis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_signal_outcomes_outcome ON signal_outcomes (outcome)")
            }
        }

        @Volatile private var instance: GoldLensDatabase? = null
        fun get(context: Context): GoldLensDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GoldLensDatabase::class.java, "goldlens-local.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}

suspend fun GoldLensDatabase.saveAnalysis(result: AnalysisEntity, zones: List<ZoneEntity>) = withTransaction {
    val id = analysisDao().insert(result)
    analysisDao().insertZones(zones.map { it.copy(analysisId = id) })
}

suspend fun GoldLensDatabase.replaceCandleSet(
    timeframe: String,
    candles: List<Candle>,
    symbol: String = "XAU/USD",
    source: DataSourceKind = DataSourceKind.IMPORTED_CSV,
    sourceLabel: String = "Imported CSV",
    sourcePrecision: Int? = null,
) = withTransaction {
    candleDao().delete(symbol, timeframe)
    candleDao().upsertAll(candles.map { it.toEntity(timeframe, symbol, source, sourceLabel, sourcePrecision) })
}

fun CandleEntity.toCandle() = Candle(timestampMillis, open, high, low, close, volume)
fun CandleEntity.toDataSourceKind() = runCatching { DataSourceKind.valueOf(source) }.getOrDefault(DataSourceKind.IMPORTED_CSV)

private fun Candle.toEntity(
    timeframe: String,
    symbol: String = "XAU/USD",
    source: DataSourceKind = DataSourceKind.IMPORTED_CSV,
    sourceLabel: String = "Imported CSV",
    sourcePrecision: Int? = null,
) = CandleEntity(
    symbol = symbol, timeframe = timeframe, timestampMillis = timestampMillis,
    open = open, high = high, low = low, close = close, volume = volume,
    source = source.name, sourceLabel = sourceLabel, sourcePrecision = sourcePrecision,
)

fun AnalysisResult.toEntity() = AnalysisEntity(
    timestampMillis = timestampMillis ?: System.currentTimeMillis(), symbol = instrument, timeframe = timeframe,
    source = dataSource.name, direction = direction.name, score = score, price = price,
    entry = entry, stopLoss = stopLoss, takeProfit = takeProfit, rewardRisk = rewardRisk,
    reason = reason, evidence = evidence.joinToString("\n"), conflicts = conflicts.joinToString("\n"),
)

fun PriceZone.toEntity(analysisId: Long = 0) = ZoneEntity(
    analysisId = analysisId, side = side.name, lower = lower, upper = upper, midpoint = midpoint,
    touches = touches, strength = strength, timeframe = timeframe, status = status.name,
    lastTouchMillis = lastTouchMillis,
)
