package app.tuxguitar.android.view.sheet;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGQuickSheetController {

	private TGQuickSheet view;

	public TGQuickSheetController() {
		super();
	}

	public TGQuickSheet getView() {
		return view;
	}

	public void setView(TGQuickSheet view) {
		this.view = view;
	}

	public void updateItems() {
		if( this.getView() != null ) {
			this.getView().updateItems();
		}
	}

	public static TGQuickSheetController getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGQuickSheetController.class.getName(), new TGSingletonFactory<TGQuickSheetController>() {
			public TGQuickSheetController createInstance(TGContext context) {
				return new TGQuickSheetController();
			}
		});
	}
}
