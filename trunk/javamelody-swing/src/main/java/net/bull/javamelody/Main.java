/*
 * Copyright 2008-2012 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import net.bull.javamelody.swing.util.MSwingUtilities;
import net.bull.javamelody.swing.util.ShadowPopupFactory;

/**
 * Classe Main.
 * @author Emeric Vernat
 */
public final class Main {
	private Main() {
		super();
	}

	/**
	 * Méthode main.
	 * @param args Arguments du programme
	 */
	public static void main(String[] args) {
		initLookAndFeel();
		ShadowPopupFactory.install();
		MSwingUtilities.initEscapeClosesDialogs();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					// affichage de la frame dans le thread AWT
					showFrame();
				} catch (final Throwable t) { // NOPMD
					MSwingUtilities.showException(t);
					exit();
				}
			}
		});
		// TODO menu pour mettre en full screen
	}

	static void showFrame() throws IOException {
		// TODO mettre les instances de MainPanel dans des onglets
		final List<URL> urls = Arrays.asList(new URL(
				"http://localhost:8090/test/monitoring?format=serialized"));
		final RemoteCollector remoteCollector = new RemoteCollector("test", urls);
		remoteCollector.collectData();
		final String collectorUrl = urls.get(0).toExternalForm();
		final URL monitoringUrl = new URL(collectorUrl.substring(0, collectorUrl.indexOf('?')));
		final MainPanel contentPane = new MainPanel(remoteCollector, monitoringUrl);
		final MainFrame frame = new MainFrame();
		frame.setContentPane(contentPane);
		frame.setVisible(true);
	}

	/**
	 * Initialisation du L&F.
	 */
	private static void initLookAndFeel() {
		// UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		for (final LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
			if ("Nimbus".equals(info.getName())) {
				try {
					UIManager.setLookAndFeel(info.getClassName());
					break;
				} catch (final Exception e) {
					throw new RuntimeException(e); // NOPMD
				}
			}
		}
	}

	@SuppressWarnings("all")
	static void exit() {
		System.exit(1); // NOPMD
	}
}
