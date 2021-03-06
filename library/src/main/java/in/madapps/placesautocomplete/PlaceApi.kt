package `in`.madapps.placesautocomplete

import `in`.madapps.placesautocomplete.listener.OnPlacesDetailsListener
import `in`.madapps.placesautocomplete.model.Address
import `in`.madapps.placesautocomplete.model.Place
import `in`.madapps.placesautocomplete.model.PlaceDetails
import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.annotation.Nullable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

/**
 * Created by mukeshsolanki on 28/02/19.
 */
class PlaceAPI {

  fun autocomplete(input: String): ArrayList<Place>? {
    if (TextUtils.isEmpty(apiKey)) {
      throw RuntimeException(appContext?.getString(R.string.error_lib_not_initialized))
    }
    val resultList: ArrayList<Place>? = null
    var conn: HttpURLConnection? = null
    val jsonResults = StringBuilder()
    try {
      val sb = StringBuilder(PLACES_API_BASE + TYPE_AUTOCOMPLETE + OUT_JSON)
      sb.append("?key=$apiKey")
      sb.append("&input=" + URLEncoder.encode(input, "utf8"))

      val url = URL(sb.toString())
      conn = url.openConnection() as HttpURLConnection
      val inputStreamReader = InputStreamReader(conn.inputStream)
      constructAutoCompleteData(inputStreamReader, jsonResults)
    } catch (e: MalformedURLException) {
      Log.e(TAG, appContext?.getString(R.string.error_processing_places_api), e)
      return resultList
    } catch (e: IOException) {
      Log.e(TAG, appContext?.getString(R.string.error_connecting_to_places_api), e)
      return resultList
    } finally {
      conn?.disconnect()
    }
    return parseAutoCompleteData(jsonResults)
  }

  private fun parseAutoCompleteData(jsonResults: StringBuilder): ArrayList<Place>? {
    var resultList: ArrayList<Place>? = ArrayList()
    try {
      // Create a JSON object hierarchy from the results
      val jsonObj = JSONObject(jsonResults.toString())
      val predsJsonArray = jsonObj.getJSONArray("predictions")

      // Extract the Place descriptions from the results
      resultList = ArrayList(predsJsonArray.length())
      for (i in 0 until predsJsonArray.length()) {
        resultList.add(
          Place(
            predsJsonArray.getJSONObject(i).getString("place_id"),
            predsJsonArray.getJSONObject(i).getString("description")
          )
        )
      }
      return resultList
    } catch (e: JSONException) {
      val errorJson = JSONObject(jsonResults.toString())
      if (errorJson.has("error_message")) {
        Log.e(TAG, errorJson.getString("error_message"))
      } else {
        Log.e(TAG, appContext?.getString(R.string.error_cannot_process_json_results), e)
      }
      return resultList
    }
  }

  private fun constructAutoCompleteData(
    inputStreamReader: InputStreamReader,
    jsonResults: StringBuilder
  ) {
    var read: Int
    val buff = CharArray(1024)
    loop@ do {
      read = inputStreamReader.read(buff)
      when {
        read != -1 -> jsonResults.append(buff, 0, read)
        else -> break@loop
      }
    } while (true)
  }

  fun initialize(key: String, context: Context) {
    apiKey = key
    appContext = context
  }

  /**
   * Fetches the details of the place
   */
  @Nullable
  fun fetchPlaceDetails(placeId: String, listener: OnPlacesDetailsListener) {
    if (TextUtils.isEmpty(apiKey)) {
      throw RuntimeException(appContext?.getString(R.string.error_lib_not_initialized))
    }

    Thread(Runnable {
      var conn: HttpURLConnection? = null
      val jsonResults = StringBuilder()
      try {
        val sb = StringBuilder(PLACES_API_BASE + TYPE_DETAIL + OUT_JSON)
        sb.append("?key=$apiKey")
        sb.append("$PARAM_PLACE_ID$placeId")
        val url = URL(sb.toString())
        conn = url.openConnection() as HttpURLConnection
        constructDetailsData(conn, jsonResults)
        parseDetailsData(jsonResults, listener)
      } catch (e: JSONException) {
        parseDetailsError(jsonResults, listener, e)
      } catch (e: MalformedURLException) {
        Log.e(TAG, appContext?.getString(R.string.error_processing_places_api), e)
        appContext?.getString(R.string.error_processing_places_api)?.let { listener.onError(it) }
      } catch (e: IOException) {
        Log.e(TAG, appContext?.getString(R.string.error_connecting_to_places_api), e)
        appContext?.getString(R.string.error_connecting_to_places_api)?.let { listener.onError(it) }
      } finally {
        conn?.disconnect()
      }
    }).start()
  }

  private fun parseDetailsError(
    jsonResults: StringBuilder,
    listener: OnPlacesDetailsListener,
    e: JSONException
  ) {
    val errorJson = JSONObject(jsonResults.toString())
    if (errorJson.has(ERROR_MESSAGE)) {
      Log.e(TAG, errorJson.getString(ERROR_MESSAGE), e)
      listener.onError(errorJson.getString(ERROR_MESSAGE))
    } else {
      Log.e(TAG, appContext?.getString(R.string.error_cannot_process_json_results), e)
      appContext?.getString(R.string.error_cannot_process_json_results)
        ?.let { listener.onError(it) }
    }
  }

  private fun parseDetailsData(jsonResults: StringBuilder, listener: OnPlacesDetailsListener) {
    val jsonObj = JSONObject(jsonResults.toString())
    val resultJsonObject = jsonObj.getJSONObject(RESULT)
    val addressArray = resultJsonObject.getJSONArray(ADDRESS_COMPONENTS)
    val address = ArrayList<Address>()
    (0 until addressArray.length()).forEach { i ->
      val addressObject = addressArray.getJSONObject(i)
      val addressTypeArray = addressObject.getJSONArray(TYPES)
      val addressType = ArrayList<String>()
      parseAddressType(addressTypeArray, addressType, address, addressObject)
    }
    listener.onPlaceDetailsFetched(
      PlaceDetails(
        resultJsonObject.getString(ID),
        resultJsonObject.getString(NAME),
        address
      )
    )
  }

  private fun parseAddressType(
    addressTypeArray: JSONArray,
    addressType: ArrayList<String>,
    address: ArrayList<Address>,
    addressObject: JSONObject
  ) {
    (0 until addressTypeArray.length()).forEach { j ->
      addressType.add(addressTypeArray.getString(j))
    }
    address.add(
      Address(
        addressObject.getString(LONG_NAME),
        addressObject.getString(SHORT_NAME),
        addressType
      )
    )
  }

  private fun constructDetailsData(conn: HttpURLConnection, jsonResults: StringBuilder) {
    val inputStreamReader = InputStreamReader(conn.inputStream)
    var read: Int
    val buff = CharArray(1024)
    loop@ do {
      read = inputStreamReader.read(buff)
      when {
        read != -1 -> jsonResults.append(buff, 0, read)
        else -> break@loop
      }
    } while (true)
  }

  companion object {
    private val TAG = PlaceAPI::class.java.simpleName
    private const val PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place"
    private const val TYPE_AUTOCOMPLETE = "/autocomplete"
    private const val TYPE_DETAIL = "/details"
    private const val PARAM_PLACE_ID = "&placeid="
    private const val OUT_JSON = "/json"
    private const val LONG_NAME = "long_name"
    private const val SHORT_NAME = "short_name"
    private const val ID = "id"
    private const val NAME = "name"
    private const val TYPES = "types"
    private const val ADDRESS_COMPONENTS = "address_components"
    private const val RESULT = "result"
    private const val ERROR_MESSAGE = "error_message"
    private var apiKey = ""
    private var appContext: Context? = null
  }
}