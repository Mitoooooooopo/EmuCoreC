#include "stdafx.h"
#include "mouse_gyro_handler.h"

#include <algorithm>

// EmuCoreC: Qt-free implementation for Android. Upstream's mouse_gyro_handler
// is GUI-only (QEvent/QWindow) and cannot compile without Qt, which the Android
// build deliberately excludes; pad_thread still references the class, so it is
// implemented here with the state machine intact but no input source -- Android
// gyro comes from the touch overlay instead.

LOG_CHANNEL(gui_log, "GUI");

void mouse_gyro_handler::clear()
{
	m_active = false;
	m_reset = false;
}

bool mouse_gyro_handler::toggle_enabled()
{
	m_enabled = !m_enabled;
	if (!m_enabled)
	{
		clear();
	}
	return m_enabled;
}

void mouse_gyro_handler::set_enabled(bool enabled)
{
	m_enabled = enabled;
	if (!m_enabled)
	{
		clear();
	}
}

void mouse_gyro_handler::handle_event(QEvent* /*ev*/, const QWindow& /*win*/)
{
	// No mouse input source on Android.
}

void mouse_gyro_handler::apply_gyro(const std::shared_ptr<Pad>& pad)
{
	// Keep the sensor state up to date so nothing lingers when the mode
	// is toggled; there is no mouse to drive it on Android.
	m_active = false;
	m_gyro_x = DEFAULT_MOTION_X;
	m_gyro_y = DEFAULT_MOTION_Y;
	m_gyro_z = DEFAULT_MOTION_Z;
}

void mouse_gyro_handler::set_gyro_active()
{
}

void mouse_gyro_handler::set_gyro_reset()
{
}

void mouse_gyro_handler::set_gyro_xz(s32 /*off_x*/, s32 /*off_y*/)
{
}

void mouse_gyro_handler::set_gyro_y(s32 /*steps*/)
{
}
