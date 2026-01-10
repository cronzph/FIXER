const { onValueCreated, onValueUpdated } = require('firebase-functions/v2/database');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { onCall } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Helper function to send notifications using the newer FCM API
 */
async function sendNotification(token, title, body, data) {
  try {
    const message = {
      token: token,
      notification: {
        title: title,
        body: body,
      },
      data: data,
      android: {
        notification: {
          sound: 'default',
          clickAction: 'FLUTTER_NOTIFICATION_CLICK',
        },
      },
      apns: {
        payload: {
          aps: {
            sound: 'default',
          },
        },
      },
    };

    const response = await admin.messaging().send(message);
    console.log('Successfully sent message:', response);
    return { success: true, response };
  } catch (error) {
    console.error('Error sending message:', error);
    
    // Handle invalid tokens
    if (error.code === 'messaging/invalid-registration-token' ||
        error.code === 'messaging/registration-token-not-registered') {
      console.log('Invalid token detected, should be removed from database');
      return { success: false, invalidToken: true };
    }
    
    return { success: false, error };
  }
}

/**
 * Helper function to send to multiple tokens
 */
async function sendToMultipleTokens(tokens, title, body, data) {
  if (!tokens || tokens.length === 0) {
    console.log('No tokens to send to');
    return { successCount: 0, failureCount: 0 };
  }

  const messages = tokens.map(token => ({
    token: token,
    notification: {
      title: title,
      body: body,
    },
    data: data,
    android: {
      notification: {
        sound: 'default',
        clickAction: 'FLUTTER_NOTIFICATION_CLICK',
      },
    },
    apns: {
      payload: {
        aps: {
          sound: 'default',
        },
      },
    },
  }));

  try {
    const response = await admin.messaging().sendEach(messages);
    console.log(`${response.successCount} messages sent successfully`);
    console.log(`${response.failureCount} messages failed`);
    
    // Log failed tokens for cleanup
    response.responses.forEach((resp, idx) => {
      if (!resp.success) {
        console.error(`Failed to send to token ${idx}:`, resp.error);
      }
    });
    
    return response;
  } catch (error) {
    console.error('Error sending batch messages:', error);
    return { successCount: 0, failureCount: tokens.length };
  }
}

/**
 * Cloud Function to send notification when new report is created
 */
exports.onNewReport = onValueCreated({
  ref: '/maintenance_reports/{reportId}',
  region: 'us-central1'
}, async (event) => {
  const reportId = event.params.reportId;
  const reportData = event.data.val();

  console.log('New report created:', reportId);

  try {
    const title = reportData.title || 'New Report';
    const priority = reportData.priority || 'medium';
    const reportedBy = reportData.reportedBy || 'Someone';

    // Get all active admins
    const adminsSnapshot = await admin.database()
      .ref('users')
      .orderByChild('role')
      .equalTo('ADMIN')
      .once('value');

    const tokens = [];
    const notificationPromises = [];

    adminsSnapshot.forEach((adminSnapshot) => {
      const adminId = adminSnapshot.key;
      const adminData = adminSnapshot.val();

      if (adminData.roleActive === true) {
        // Get token for this admin
        notificationPromises.push(
          admin.database()
            .ref(`user_tokens/${adminId}`)
            .once('value')
            .then((tokenSnapshot) => {
              const token = tokenSnapshot.val()?.token;
              if (token) tokens.push(token);
            })
        );

        // Save notification to database
        const notificationData = {
          title: 'New Maintenance Report',
          body: `${reportedBy} reported: ${title} (${priority.toUpperCase()})`,
          type: 'new_report',
          reportId,
          priority,
          timestamp: Date.now(),
          read: false
        };

        notificationPromises.push(
          admin.database()
            .ref(`user_notifications/${adminId}`)
            .push(notificationData)
        );
      }
    });

    await Promise.all(notificationPromises);

    if (tokens.length === 0) {
      console.log('No admin tokens found');
      return null;
    }

    // Send push notifications
    const notificationTitle = 'New Maintenance Report';
    const notificationBody = `${reportedBy} reported: ${title} (${priority.toUpperCase()})`;
    const notificationData = {
      type: 'new_report',
      reportId: reportId,
      priority: priority,
      click_action: 'FLUTTER_NOTIFICATION_CLICK'
    };

    const response = await sendToMultipleTokens(
      tokens,
      notificationTitle,
      notificationBody,
      notificationData
    );

    console.log('Notifications sent:', response.successCount, 'success,', response.failureCount, 'failed');
    return response;
  } catch (error) {
    console.error('Error sending notification:', error);
    return null;
  }
});

/**
 * Cloud Function to send notification when report status changes
 */
exports.onReportStatusChange = onValueUpdated({
  ref: '/maintenance_reports/{reportId}/status',
  region: 'us-central1'
}, async (event) => {
  const reportId = event.params.reportId;
  const newStatus = event.data.after.val();
  const oldStatus = event.data.before.val();

  if (newStatus === oldStatus) return null;

  console.log(`Report ${reportId} status changed from ${oldStatus} to ${newStatus}`);

  try {
    const reportSnapshot = await admin.database()
      .ref(`maintenance_reports/${reportId}`)
      .once('value');

    const reportData = reportSnapshot.val();
    if (!reportData) return null;

    const title = reportData.title || 'Report';
    const reporterUid = reportData.reportedByUid;
    if (!reporterUid) return null;

    const tokenSnapshot = await admin.database()
      .ref(`user_tokens/${reporterUid}`)
      .once('value');

    const token = tokenSnapshot.val()?.token;
    if (!token) {
      console.log('No token found for reporter');
      return null;
    }

    // Save notification to database
    const notificationData = {
      title: 'Report Status Update',
      body: `Your report '${title}' is now ${newStatus.toUpperCase()}`,
      type: 'status_change',
      reportId,
      priority: (newStatus === 'completed' || newStatus === 'rejected') ? 'high' : 'medium',
      timestamp: Date.now(),
      read: false
    };

    await admin.database()
      .ref(`user_notifications/${reporterUid}`)
      .push(notificationData);

    // Send push notification
    const response = await sendNotification(
      token,
      'Report Status Update',
      `Your report '${title}' is now ${newStatus.toUpperCase()}`,
      {
        type: 'status_change',
        reportId: reportId,
        status: newStatus,
        click_action: 'FLUTTER_NOTIFICATION_CLICK'
      }
    );

    console.log('Status change notification sent:', response);
    return response;
  } catch (error) {
    console.error('Error sending status change notification:', error);
    return null;
  }
});

/**
 * Cloud Function to notify technician when assigned
 */
exports.onTaskAssigned = onValueCreated({
  ref: '/maintenance_reports/{reportId}/assignedTo',
  region: 'us-central1'
}, async (event) => {
  const reportId = event.params.reportId;
  const technicianEmail = event.data.val();

  if (!technicianEmail) return null;

  console.log(`Task ${reportId} assigned to ${technicianEmail}`);

  try {
    const reportSnapshot = await admin.database()
      .ref(`maintenance_reports/${reportId}`)
      .once('value');

    const reportData = reportSnapshot.val();
    const title = reportData.title || 'Maintenance Task';
    const scheduledDate = reportData.scheduledDate || '';

    const usersSnapshot = await admin.database()
      .ref('users')
      .orderByChild('email')
      .equalTo(technicianEmail)
      .once('value');

    let technicianUid = null;
    usersSnapshot.forEach((userSnapshot) => {
      technicianUid = userSnapshot.key;
    });

    if (!technicianUid) {
      console.log('Technician UID not found');
      return null;
    }

    const tokenSnapshot = await admin.database()
      .ref(`user_tokens/${technicianUid}`)
      .once('value');

    const token = tokenSnapshot.val()?.token;
    if (!token) {
      console.log('No token found for technician');
      return null;
    }

    let body = `You have been assigned: ${title}`;
    if (scheduledDate) body += ` (Scheduled: ${scheduledDate})`;

    // Save notification to database
    const notificationData = {
      title: 'New Task Assigned',
      body,
      type: 'task_assigned',
      reportId,
      priority: 'high',
      timestamp: Date.now(),
      read: false
    };

    await admin.database()
      .ref(`user_notifications/${technicianUid}`)
      .push(notificationData);

    // Send push notification
    const response = await sendNotification(
      token,
      'New Task Assigned',
      body,
      {
        type: 'task_assigned',
        reportId: reportId,
        click_action: 'FLUTTER_NOTIFICATION_CLICK'
      }
    );

    console.log('Task assignment notification sent:', response);
    return response;
  } catch (error) {
    console.error('Error sending task assignment notification:', error);
    return null;
  }
});

/**
 * Cloud Function to notify when task is completed
 */
exports.onTaskCompleted = onValueCreated({
  ref: '/maintenance_reports/{reportId}/completedAt',
  region: 'us-central1'
}, async (event) => {
  const reportId = event.params.reportId;
  const completedAt = event.data.val();

  if (!completedAt) return null;

  console.log(`Task ${reportId} completed`);

  try {
    const reportSnapshot = await admin.database()
      .ref(`maintenance_reports/${reportId}`)
      .once('value');

    const reportData = reportSnapshot.val();
    const title = reportData.title || 'Task';
    const completedBy = reportData.completedBy || 'Technician';
    const reporterUid = reportData.reportedByUid;

    const adminsSnapshot = await admin.database()
      .ref('users')
      .orderByChild('role')
      .equalTo('ADMIN')
      .once('value');

    const adminNotificationPromises = [];
    const adminTokens = [];

    adminsSnapshot.forEach((adminSnapshot) => {
      const adminId = adminSnapshot.key;
      const adminData = adminSnapshot.val();

      if (adminData.roleActive === true) {
        // Get admin token
        adminNotificationPromises.push(
          admin.database()
            .ref(`user_tokens/${adminId}`)
            .once('value')
            .then((tokenSnapshot) => {
              const token = tokenSnapshot.val()?.token;
              if (token) adminTokens.push(token);
            })
        );

        // Save notification to database
        const notificationData = {
          title: 'Task Completed',
          body: `${completedBy} completed: ${title}`,
          type: 'report_completed',
          reportId,
          priority: 'medium',
          timestamp: Date.now(),
          read: false
        };

        adminNotificationPromises.push(
          admin.database()
            .ref(`user_notifications/${adminId}`)
            .push(notificationData)
        );
      }
    });

    await Promise.all(adminNotificationPromises);

    // Send to admins
    if (adminTokens.length > 0) {
      await sendToMultipleTokens(
        adminTokens,
        'Task Completed',
        `${completedBy} completed: ${title}`,
        {
          type: 'report_completed',
          reportId: reportId,
          click_action: 'FLUTTER_NOTIFICATION_CLICK'
        }
      );
    }

    // Notify the original reporter
    if (reporterUid) {
      const tokenSnapshot = await admin.database()
        .ref(`user_tokens/${reporterUid}`)
        .once('value');

      const token = tokenSnapshot.val()?.token;

      const notificationData = {
        title: '✅ Your Report is Complete',
        body: `Your maintenance request '${title}' has been completed by ${completedBy}`,
        type: 'user_report_completed',
        reportId,
        priority: 'high',
        timestamp: Date.now(),
        read: false
      };

      await admin.database()
        .ref(`user_notifications/${reporterUid}`)
        .push(notificationData);

      if (token) {
        await sendNotification(
          token,
          '✅ Your Report is Complete',
          `Your maintenance request '${title}' has been completed`,
          {
            type: 'user_report_completed',
            reportId: reportId,
            click_action: 'FLUTTER_NOTIFICATION_CLICK'
          }
        );
      }
    }

    console.log('Task completion notifications sent');
    return null;
  } catch (error) {
    console.error('Error sending task completion notification:', error);
    return null;
  }
});