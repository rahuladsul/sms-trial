package dtd.phs.sil;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.devsmart.android.ui.HorizontalListView;

import dtd.phs.sil.data.DataCenter;
import dtd.phs.sil.entities.PendingMessageItem;
import dtd.phs.sil.entities.SentMessageItem;
import dtd.phs.sil.ui.AlertHelpers;
import dtd.phs.sil.ui.ChooseDateDialog;
import dtd.phs.sil.ui.ChooseFrequencyDialog;
import dtd.phs.sil.ui.ChooseTimeDialog;
import dtd.phs.sil.ui.AlertHelpers.AlertTypes;
import dtd.phs.sil.ui.auto_complete_contacts.ContactItem;
import dtd.phs.sil.ui.auto_complete_contacts.ContactsList;
import dtd.phs.sil.ui.auto_complete_contacts.IFilterListener;
import dtd.phs.sil.ui.auto_complete_contacts.MyAdapter;
import dtd.phs.sil.ui.auto_complete_contacts.SelectedContactsAdapter;
import dtd.phs.sil.utils.FrequencyHelpers;
import dtd.phs.sil.utils.Helpers;
import dtd.phs.sil.utils.Logger;
import dtd.phs.sil.utils.FrequencyHelpers.Frequencies;

public class EditMessage 
extends Activity 
implements IFilterListener {

	protected static final String EDIT_TYPE = "edit_type";
	protected static final String TYPE_NEW = "type_new";

	protected static final int DIALOG_DATE = 0;
	protected static final int DIALOG_TIME = 1;
	protected static final int DIALOG_FREQ = 2;
	protected static final int DIALOG_ALERT = 3;

	protected static final int FRAME_FILL_INFO = 0;
	protected static final int FRAME_CONTACTS_LIST = 1;
	private static final long DELTA_TIME = 59*1000; 

	//Set passedMessage = null -> Add new message , otherwise: edit
	public static PendingMessageItem passedMessage = null;
	public static SentMessageItem passedSentMessage = null;

	private TextView tvDate;
	private TextView tvTime;

	protected Calendar selectedCalendar = null;
	private FrameLayout mainFrames;
	private ListView contactsList;
	private EditText etContact;
	private MyAdapter adapter;
	private HorizontalListView lvSelectedContacts;
	private SelectedContactsAdapter selectedAdapter;
	private Resources res;
	private TextView tvFreq;
	private TextView tvAlert;
	private TextView tvCountWords;
	private EditText etMessage;
	private SmsManager sms;
	protected Frequencies frequency;
	protected AlertTypes alertType;
	private Button btOk;
	private Button btCancel;
	private boolean isEditView;

	private PendingMessageItem beingEditedMessage;
	private Button btAddContact;
	private boolean isEditSentMessage;


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.edit_message);
		init();
		createViews();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Helpers.showOnlyView(mainFrames, FRAME_FILL_INFO);
		if ( !isEditView )
			selectedCalendar = Calendar.getInstance();
		else {
			Logger.logInfo("Hide keyboard is called !");
			Helpers.hideSoftKeyboard(this);
		}
	}
	
	private void init() {
		selectedCalendar = Calendar.getInstance();
		res = getResources();
		sms = SmsManager.getDefault();
		frequency = Frequencies.ONCE;
		alertType = AlertTypes.SILENT;
		if (EditMessage.passedMessage != null) {
			isEditView = true;
			beingEditedMessage = EditMessage.passedMessage;		
			EditMessage.passedMessage = null;
			getInfoFromBeingEditedMessage(beingEditedMessage);
		}

		if (EditMessage.passedSentMessage != null) {
			Logger.logInfo("A sent message is passed !");
			isEditView = true;
			long pendingId = EditMessage.passedSentMessage.getPendingId();
			Logger.logInfo("Pending message id: " + pendingId);
			beingEditedMessage = DataCenter.getPendingMessageWithId(getApplicationContext(), pendingId);
			
			if ( beingEditedMessage == null ) {
				Logger.logInfo("No pending message with id: " + pendingId + " exists");	
				beingEditedMessage = PendingMessageItem.createFromSentItem(EditMessage.passedSentMessage);
				isEditSentMessage = true;
			}
			EditMessage.passedSentMessage = null;
			getInfoFromBeingEditedMessage(beingEditedMessage);
		}

	}

	private void getInfoFromBeingEditedMessage(PendingMessageItem beingEditedMessage2) {
		selectedCalendar = beingEditedMessage.getStartDateTime();
		frequency = beingEditedMessage.getFreq();
		alertType = beingEditedMessage.getAlert();
	}

	private void createViews() {
		createAutoContactModules();
		createOptionViews();
		createMessageViews();
		createButtons();
	}

	private void createButtons() {
		btOk = (Button) findViewById(R.id.btOk);
		btOk.setText(res.getString(R.string.Add));		
		btOk.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if ( isValidMessage() ) {
					DataCenter.savePendingMessageItem(getApplicationContext(),createPendingMessage());
					onBackPressed();
				} else {
					showInvalidMessageToast();
				}
			}
		});
		if ( isEditView ) {
			btOk.setText(res.getString(R.string.Save));
			btOk.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if ( isValidMessage() ) {
						if ( !isEditSentMessage) {
							long id = beingEditedMessage.getId();
							DataCenter.modifyPendingMessage(getApplicationContext(),id,createPendingMessage());
						} else {
							DataCenter.savePendingMessageItem(getApplicationContext(), createPendingMessage());
						}
						onBackPressed();
					} else {
						showInvalidMessageToast();
					}
				}
			});
		}

		btCancel = (Button) findViewById(R.id.btCancel);
		btCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onBackPressed();
			}
		});

		btAddContact = (Button) findViewById(R.id.btAddContact);
		btAddContact.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String str = etContact.getText().toString();

				if ( Helpers.isPhoneNumber(str)) {
					str = Helpers.parsePhoneNumber(str);
					ContactItem item = new ContactItem(str, str, System.currentTimeMillis());
					Logger.logInfo("Button add contact pressed !");
					Helpers.startAfter(300, new Runnable() {
						@Override
						public void run() {
							btAddContact.post(new Runnable() {
								@Override
								public void run() {
									Logger.logInfo("Button add visibility changed !");
									btAddContact.setVisibility(View.GONE);							
								}
							});
						}
					});
					onContactAdded(item);
				} else {
					btAddContact.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getApplicationContext(), R.string.Please_enter_a_phone_number, Toast.LENGTH_LONG).show();
						}
					});

				}
			}
		});
	}

	protected boolean isValidMessage() {
		ContactsList selectedList = selectedAdapter.getSelectedList();
		if ( selectedList.isEmpty() ) return false;
		String content = etMessage.getText().toString();
		if ( content.trim().length() == 0 ) return false;
		return true;
	}

	protected void showInvalidMessageToast() {
		Toast.makeText(getApplicationContext(), res.getString(R.string.Invalid_message_warning), Toast.LENGTH_LONG).show();
	}

	protected PendingMessageItem createPendingMessage() {
		dtd.phs.sil.ui.auto_complete_contacts.ContactsList contacts = selectedAdapter.getSelectedList();
		return PendingMessageItem.createInstance(
				0,
				contacts.getNames(),
				contacts.getNumbers(), 
				etMessage.getText().toString(), 
				selectedCalendar.getTimeInMillis(), 
				FrequencyHelpers.indexOf(frequency), 
				AlertHelpers.indexOf(alertType)
		);
	}

	private void createMessageViews() {
		tvCountWords = (TextView) findViewById(R.id.tvCountWords);
		etMessage = (EditText) findViewById(R.id.etMessage);
		etMessage.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,int after) {}
			@Override
			public void afterTextChanged(final Editable s) {
				String content = s.toString();
				final ArrayList<String> parts = sms.divideMessage(content);
				tvCountWords.post(new Runnable() {
					@Override
					public void run() {
						tvCountWords.setText(res.getString(R.string.Words)+": "  + s.length()+ " / " + parts.size());
					}
				});
			}
		});

		if ( isEditView ) {
			etMessage.setText(beingEditedMessage.getContent());
		}
	}

	private void createOptionViews() {
		tvDate = getTextView(
				R.id.dateLine,
				res.getString(R.string.Date),
				new OnClickListener() {

					@Override
					public void onClick(View v) {
						showDialog(DIALOG_DATE);
					}
				}				
		);
		tvTime = getTextView(
				R.id.timeLine, 
				res.getString(R.string.Time),
				new OnClickListener() {

					@Override
					public void onClick(View v) {
						showDialog(DIALOG_TIME);
					}
				}		
		);


		updateTimeDateTexts(selectedCalendar);

		tvFreq = getTextView(R.id.frequencyLine, res.getString(R.string.Frequency),new OnClickListener() {

			@Override
			public void onClick(View v) {
				showDialog(DIALOG_FREQ);
			}
		});
		updateFrequency();

		tvAlert = getTextView(R.id.alertLine, res.getString(R.string.Alert_on_delivery),new OnClickListener() {

			@Override
			public void onClick(View v) {
				showDialog(DIALOG_ALERT);
			}
		});

		updateAlert();
	}

	private void updateFrequency() {
		tvFreq.setText(FrequencyHelpers.mapFreq2Str.get(frequency));
	}

	private TextView getTextView(int layoutId, String title, OnClickListener onClickListener) {
		View v = findViewById(layoutId);
		TextView tvTitle = (TextView) v.findViewById(R.id.tvTitle);
		tvTitle.setText(title);
		TextView tv = (TextView) v.findViewById(R.id.tvText);
		v.setOnClickListener(onClickListener);
		return tv;
	}

	private void createAutoContactModules() {

		mainFrames = (FrameLayout) findViewById(R.id.mainFrames);
		Helpers.showOnlyView(mainFrames, FRAME_FILL_INFO);

		etContact = (EditText) findViewById(R.id.etTo);
		contactsList = (ListView) findViewById(R.id.listAutoComplete);
		adapter = new MyAdapter(getApplicationContext(),this);

		adapter.loadAllContacts();
		contactsList.setAdapter(adapter);

		lvSelectedContacts = (HorizontalListView) findViewById(R.id.listSelected);
		lvSelectedContacts.setVisibility(View.GONE);		

		selectedAdapter = new SelectedContactsAdapter(getApplicationContext()) {
			@Override
			public void onItemRemoved(int position) {
				if (selectedAdapter.getCount() == 0) {
					lvSelectedContacts.setVisibility(View.GONE);
				}
			}
		};

		if ( isEditView ) {
			ContactsList selectedContacts = ContactsList.createContactsWithoutLastContactedTime(beingEditedMessage);
			selectedAdapter.setSelectedList(selectedContacts);
			lvSelectedContacts.setVisibility(View.VISIBLE);
		}
		lvSelectedContacts.setAdapter(selectedAdapter);

		contactsList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position,
					long arg3) {
				ContactItem contact = adapter.getContact(position);
				onContactAdded(contact);
			}


		});

		etContact.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,int after) {}
			@Override
			public void afterTextChanged(Editable s) {
				String text = s.toString();
				Helpers.showOnlyView(mainFrames, FRAME_CONTACTS_LIST);
				adapter.getFilter().filter(text);
			}
		});

		Helpers.showOnlyView(mainFrames, FRAME_FILL_INFO);
	}

	private void onContactAdded(ContactItem contact) {
		selectedAdapter.addContact(contact);
		selectedAdapter.notifyDataSetChanged();
		lvSelectedContacts.setVisibility(View.VISIBLE);
		etContact.setText("");
		onBackPressed();
	}	

	@Override
	public void onBackPressed() {
		if (mainFrames.getChildAt(FRAME_CONTACTS_LIST).getVisibility() == View.VISIBLE) {
			Helpers.showOnlyView(mainFrames, FRAME_FILL_INFO);
		} else {
			
			super.onBackPressed();
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_DATE:
			return new ChooseDateDialog(this) {
				@Override
				public void onDateSelected(Calendar selectedCalendar) {
					EditMessage.this.selectedCalendar = selectedCalendar;
					updateTimeDateTexts(selectedCalendar);
				}
			};
		case DIALOG_TIME:
			return new ChooseTimeDialog(this) {

				@Override
				public void onCalendarSelected(Calendar selectedCalendar) {
					EditMessage.this.selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedCalendar.get(Calendar.HOUR_OF_DAY));
					EditMessage.this.selectedCalendar.set(Calendar.MINUTE, selectedCalendar.get(Calendar.MINUTE));
					updateTimeDateTexts(selectedCalendar);
				}
			};
		case DIALOG_FREQ:
			return new ChooseFrequencyDialog(this,R.string.Choose_Frequency,FrequencyHelpers.FREQ_NAMES) {

				@Override
				public void onItemSelected(int postion) {
					frequency = FrequencyHelpers.FREQUENCIES[postion];
					updateFrequency();
				}
			};
		case DIALOG_ALERT:
			return new ChooseFrequencyDialog(this,R.string.Alert_on_delivery,AlertHelpers.ALERT_STRINGS) {

				@Override
				public void onItemSelected(int position) {
					alertType = AlertHelpers.ALERT_TYPE[position];
					updateAlert();
				}
			};

		}

		return null;
	}

	protected void updateAlert() {
		tvAlert.setText(AlertHelpers.mapAlertType2Str.get(alertType));
	}

	protected void updateTimeDateTexts(Calendar calendar) {
		int y = calendar.get(Calendar.YEAR);
		int m = calendar.get(Calendar.MONTH);
		int d = calendar.get(Calendar.DATE);
		int h = calendar.get(Calendar.HOUR_OF_DAY);
		int min = calendar.get(Calendar.MINUTE);
		tvDate.setText(new SimpleDateFormat("EEEE - MMMM.dd, yyyy").format(new Date(y-1900,m,d)));
		tvTime.setText(new SimpleDateFormat("HH:mm").format(new Time(h, min, 0))); 
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case DIALOG_DATE:
			((ChooseDateDialog)dialog).prepare(selectedCalendar);
			break;
		case DIALOG_TIME:
			((ChooseTimeDialog)dialog).prepare(selectedCalendar);
			break;
		case DIALOG_FREQ:
			break;
		}
	}

	@Override
	public void onPublishResult(Object data) {
		dtd.phs.sil.ui.auto_complete_contacts.ContactsList list = 
			(dtd.phs.sil.ui.auto_complete_contacts.ContactsList) data;
		if ( list.size() == 0) {
			btAddContact.setVisibility(View.VISIBLE);
		} else {
			btAddContact.setVisibility(View.GONE);
		}
	}

}