//! Version numbers as LVM compares them.

use std::cmp::Ordering;

/// Compares dotted numeric versions; anything after `-`, such as `-SNAPSHOT` or `-RC1`, is ignored, as blueprint does.
pub fn compare(left: &str, right: &str) -> Ordering {
    let (left, right) = (numbers(left), numbers(right));
    (0..left.len().max(right.len()))
        .map(|i| left.get(i).copied().unwrap_or(0).cmp(&right.get(i).copied().unwrap_or(0)))
        .find(|ordering| ordering.is_ne())
        .unwrap_or(Ordering::Equal)
}

fn numbers(version: &str) -> Vec<u64> {
    version.split('-').next().unwrap_or_default().split('.').map(|part| part.parse().unwrap_or(0)).collect()
}

/// How a tool's version relates to the versions LVM supports.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Support {
    TooOld,
    Supported,
    NewerThanTested,
    Unknown,
}

pub fn support(actual: Option<&str>, minimum: Option<&str>, tested: Option<&str>) -> Support {
    let Some(actual) = actual else { return Support::Unknown };
    if minimum.is_some_and(|minimum| compare(actual, minimum).is_lt()) {
        return Support::TooOld;
    }
    match tested {
        Some(tested) if compare(actual, tested).is_gt() => Support::NewerThanTested,
        Some(_) => Support::Supported,
        None if minimum.is_some() => Support::Supported,
        None => Support::Unknown,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compares_numbers_not_text_and_ignores_qualifiers() {
        assert!(compare("9.10", "9.9.1").is_gt());
        assert!(compare("0.4.0-SNAPSHOT", "0.4.0").is_eq());
        assert!(compare("2.4", "2.4.0").is_eq());
        assert!(compare("0.3.2", "0.4.0").is_lt());
    }

    #[test]
    fn tells_too_old_supported_and_newer_than_tested() {
        assert_eq!(support(Some("9.5"), Some("9.6"), Some("9.9.1")), Support::TooOld);
        assert_eq!(support(Some("9.9.1"), Some("9.6"), Some("9.9.1")), Support::Supported);
        assert_eq!(support(Some("9.10"), Some("9.6"), Some("9.9.1")), Support::NewerThanTested);
        assert_eq!(support(None, Some("9.6"), None), Support::Unknown);
    }
}
