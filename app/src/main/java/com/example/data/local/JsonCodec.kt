package com.example.data.local

import com.example.domain.FinalScores
import com.example.domain.PauseInterval
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class PauseIntervalJson(
  val pausedAt: Long,
  val resumedAt: Long? = null,
)

@JsonClass(generateAdapter = true)
data class FinalScoresJson(
  val teamA: Int,
  val teamB: Int,
  val teamC: Int? = null,
)

object JsonCodec {
  val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val stringListAdapter: JsonAdapter<List<String>> = moshi.adapter(
    Types.newParameterizedType(List::class.java, String::class.java),
  )
  private val stringNestedListAdapter: JsonAdapter<List<List<String>>> = moshi.adapter(
    Types.newParameterizedType(
      List::class.java,
      Types.newParameterizedType(List::class.java, String::class.java),
    ),
  )
  private val intListAdapter: JsonAdapter<List<Int>> = moshi.adapter(
    Types.newParameterizedType(List::class.java, Integer::class.java),
  )
  private val booleanListAdapter: JsonAdapter<List<Boolean>> = moshi.adapter(
    Types.newParameterizedType(List::class.java, java.lang.Boolean::class.java),
  )
  private val pauseListAdapter: JsonAdapter<List<PauseIntervalJson>> = moshi.adapter(
    Types.newParameterizedType(List::class.java, PauseIntervalJson::class.java),
  )
  private val finalScoresAdapter: JsonAdapter<FinalScoresJson> =
    moshi.adapter(FinalScoresJson::class.java)

  fun encodeStringList(list: List<String>): String = stringListAdapter.toJson(list)
  fun decodeStringList(json: String?): List<String> =
    if (json.isNullOrBlank()) emptyList() else runCatching { stringListAdapter.fromJson(json) }.getOrNull().orEmpty()

  fun encodeNestedStringList(list: List<List<String>>): String = stringNestedListAdapter.toJson(list)
  fun decodeNestedStringList(json: String?): List<List<String>> =
    if (json.isNullOrBlank()) emptyList() else runCatching { stringNestedListAdapter.fromJson(json) }.getOrNull().orEmpty()

  fun encodeIntList(list: List<Int>): String = intListAdapter.toJson(list)
  fun decodeIntList(json: String?): List<Int> =
    if (json.isNullOrBlank()) emptyList() else runCatching { intListAdapter.fromJson(json) }.getOrNull().orEmpty()

  fun encodeBooleanList(list: List<Boolean>): String = booleanListAdapter.toJson(list)
  fun decodeBooleanList(json: String?): List<Boolean> =
    if (json.isNullOrBlank()) emptyList() else runCatching { booleanListAdapter.fromJson(json) }.getOrNull().orEmpty()

  fun encodePauseLog(list: List<PauseInterval>): String =
    pauseListAdapter.toJson(list.map { PauseIntervalJson(it.pausedAt, it.resumedAt) })

  fun decodePauseLog(json: String?): List<PauseInterval> =
    if (json.isNullOrBlank()) {
      emptyList()
    } else {
      runCatching { pauseListAdapter.fromJson(json) }.getOrNull()
        .orEmpty()
        .map { PauseInterval(it.pausedAt, it.resumedAt) }
    }

  fun encodeFinalScores(scores: FinalScores?): String? =
    scores?.let { finalScoresAdapter.toJson(FinalScoresJson(it.teamA, it.teamB, it.teamC)) }

  fun decodeFinalScores(json: String?): FinalScores? =
    if (json.isNullOrBlank()) {
      null
    } else {
      runCatching { finalScoresAdapter.fromJson(json) }.getOrNull()?.let {
        FinalScores(it.teamA, it.teamB, it.teamC)
      }
    }
}
