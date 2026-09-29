package app.tuxguitar.android.view.keyboard;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGTabKeyboardController {

	private TGTabKeyboard view;

	public TGTabKeyboardController() {
		super();
	}

	public TGTabKeyboard getView() {
		return view;
	}

	public void setView(TGTabKeyboard view) {
		this.view = view;
	}

	public void toggleVisibility() {
		if( this.getView() != null ) {
			this.getView().toggleVisibility();
		}
	}

	public void show() {
		if( this.getView() != null ) {
			this.getView().show();
		}
	}

	public void hide() {
		if( this.getView() != null ) {
			this.getView().hide();
		}
	}

	public void updateMode() {
		if( this.getView() != null ) {
			this.getView().updateMode();
		}
	}

	public static TGTabKeyboardController getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGTabKeyboardController.class.getName(), new TGSingletonFactory<TGTabKeyboardController>() {
			public TGTabKeyboardController createInstance(TGContext context) {
				return new TGTabKeyboardController();
			}
		});
	}
}
