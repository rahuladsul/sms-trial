package hdcenter.vn.data.requests;

import hdcenter.vn.entities.MovieItem;
import hdcenter.vn.entities.MoviesList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Pair;

public abstract class RequestMoviesList extends Request {

	private static final String TOTAL = "total";
	private static final String RESULT = "result";

	private int page;
	public RequestMoviesList(int page) {
		super();
		this.page = page;
	}

	@Override
	protected Pair<Integer, MoviesList> parseData(String resultString) throws JSONException {
		JSONObject jobject = new JSONObject(resultString);
		MoviesList list = new MoviesList();
		int total = jobject.getInt(TOTAL);
		if ( total != 0) {
			JSONArray jarray = jobject.getJSONArray(RESULT);
			for(int i = 0; i < jarray.length() ; i++) {
				MovieItem item  = MovieItem.createFromJSONObject(jarray.getJSONObject(i));
				list.add(item);
			}
		}
		return new Pair<Integer,MoviesList>(total,list);
	}
	
	public void setPage(int page) {
		this.page = page;
	}
	
	public int getPage() {
		return page;
	}
	
}