/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2004, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.server.forclients;

import java.util.Vector;

import org.martus.common.bulletin.Bulletin;
import org.martus.common.bulletin.BulletinSaver;
import org.martus.common.crypto.MockMartusSecurity;
import org.martus.common.database.Database;
import org.martus.common.database.DatabaseKey;
import org.martus.common.database.MockServerDatabase;
import org.martus.common.network.NetworkInterfaceConstants;
import org.martus.common.packet.BulletinHeaderPacket;
import org.martus.server.main.MartusServer;
import org.martus.util.TestCaseEnhanced;


public class TestSummaryCollector extends TestCaseEnhanced
{
	public TestSummaryCollector(String name)
	{
		super(name);
	}
	
	public void setUp() throws Exception
	{
		server = new MockMartusServer();
		server.initializeBulletinStore(new MockServerDatabase());
		authorSecurity = MockMartusSecurity.createClient();
		Database db = server.getDatabase();

		original = new Bulletin(authorSecurity);
		original.setSealed();
		BulletinSaver.saveToClientDatabase(original, db, false, authorSecurity);
		
		firstClone = new Bulletin(authorSecurity);
		firstClone.createDraftCopyOf(original, db);
		firstClone.setSealed();
		BulletinSaver.saveToClientDatabase(firstClone, db, false, authorSecurity);

		clone = new Bulletin(authorSecurity); 
		clone.createDraftCopyOf(firstClone, db);
		BulletinSaver.saveToClientDatabase(clone, db, false, authorSecurity);
		
	}

	class MockSummaryCollector extends SummaryCollector
	{
		protected MockSummaryCollector(MartusServer serverToUse, String authorAccountToUse, Vector retrieveTagsToUse)
		{
			super(serverToUse, authorAccountToUse, retrieveTagsToUse);
		}

		public String callerAccountId()
		{
			return authorAccountId;
		}

		public boolean isWanted(DatabaseKey key)
		{
			return true;
		}

		public boolean isAuthorized(BulletinHeaderPacket bhp)
		{
			return bhp.getAccountId().equals(callerAccountId());
		}
		
	}
	
	public void testSummarCollectorOmitsOldVersions() throws Exception
	{
		
		Vector leafUids = server.getStore().getAllBulletinUids();
		assertEquals(1, leafUids.size());
		
		String authorId = authorSecurity.getPublicKeyString();
		SummaryCollector collector = new MockSummaryCollector(server, authorId, new Vector());
		Vector localIds = collector.collectSummaries();
		assertEquals(1, localIds.size());
		assertEquals(clone.getLocalId() + "=" + clone.getFieldDataPacket().getLocalId(), localIds.get(0));
	}
	
	public void testTags() throws Exception
	{
		Database db = server.getDatabase();
		BulletinHeaderPacket bhp = clone.getBulletinHeaderPacket();
		String fdpLocalId = bhp.getFieldDataPacketId();
		Vector tags = new Vector();
		
		String minimalSummary = bhp.getLocalId() + "=" + fdpLocalId;
		
		String noTags = SummaryCollector.extractSummary(bhp, db, tags);
		assertEquals(minimalSummary, noTags);
		
		String history = original.getLocalId() + " " + firstClone.getLocalId() + " ";

		
		String expectedSizeDateHistory = minimalSummary + "=0=" + bhp.getLastSavedTime() + "=" + history;

		tags.add(NetworkInterfaceConstants.TAG_BULLETIN_SIZE);
		tags.add(NetworkInterfaceConstants.TAG_BULLETIN_DATE_SAVED);
		tags.add(NetworkInterfaceConstants.TAG_BULLETIN_HISTORY);
		String gotSizeDateHistory = SummaryCollector.extractSummary(bhp, db, tags);
		assertEquals(expectedSizeDateHistory, gotSizeDateHistory);
		
		
		String expectedHistoryDateSize = minimalSummary + "=" + history + "=" + bhp.getLastSavedTime() +"=0";

		tags.clear();
		tags.add(NetworkInterfaceConstants.TAG_BULLETIN_HISTORY);
		tags.add(NetworkInterfaceConstants.TAG_BULLETIN_DATE_SAVED);
		tags.add(NetworkInterfaceConstants.TAG_BULLETIN_SIZE);
		String gotHistoryDateSize = SummaryCollector.extractSummary(bhp, db, tags);
		assertEquals(expectedHistoryDateSize, gotHistoryDateSize);
	}

	MockMartusServer server;
	MockMartusSecurity authorSecurity;
	Bulletin original;
	Bulletin firstClone;
	Bulletin clone;
}
